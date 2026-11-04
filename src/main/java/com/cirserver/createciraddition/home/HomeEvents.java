package com.cirserver.createciraddition.home;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 基地系统的初始化与命令注册。
 *
 * 首次进入新地图时自动把出生点设为基地；之后要改位置用
 */
@EventBusSubscriber(
   modid = "create_cir_addition",
   bus = EventBusSubscriber.Bus.GAME
)
public class HomeEvents {

    /** 强制清扫倒计时剩余 tick，-1 表示没有进行中的倒计时 */
    private static int forceClearTicks = -1;

    /** 倒计时播报的时间点（秒），第一个由 startForceCleanup 直接发 */
    private static final int[] FORCE_CLEAR_MARKS = {10, 5, 3, 2, 1};

    private static void startForceCleanup(MinecraftServer server) {
        if (forceClearTicks >= 0) {
            return;
        }
        forceClearTicks = 20 * 20;
        broadcastForceClear(server, 20);
    }

    /** 驱动强制清扫倒计时 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (forceClearTicks < 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        forceClearTicks--;
        if (forceClearTicks % 20 == 0) {
            int secondsLeft = forceClearTicks / 20;
            for (int mark : FORCE_CLEAR_MARKS) {
                if (mark == secondsLeft) {
                    broadcastForceClear(server, secondsLeft);
                    break;
                }
            }
        }
        if (forceClearTicks <= 0) {
            forceClearTicks = -1;
            com.cirserver.createciraddition.cleanup.ItemCleaner.forceCleanup(server);
        }
    }

    private static void broadcastForceClear(MinecraftServer server, int seconds) {
        MutableComponent message = Component.translatable("cir.forceclear.before")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
            .append(Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            .append(Component.translatable("cir.forceclear.after").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            .append(Component.translatable("cir.forceclear.warn").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            .append(Component.translatable("cir.forceclear.end").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /** 服务器启动完成：固化量子环总开关，并在基地未初始化时用出生点补上 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            MinecraftServer server = event.getServer();

            // 量子环限制总开关：只在启动时读一次，之后 /reload 和配置热重载都改不动
            com.cirserver.createciraddition.quantum.QuantumLimiterSwitch.init();

            HomeData data = HomeData.get(server);
            if (!data.isInitialized()) {
                data.initFromSpawn(server.overworld());
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: Base initialization failed", t);
        }
    }

    /** 注册 /cir 命令 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cir")
                // 根节点不设权限，每个子命令自己声明，这样普通玩家也能开垃圾桶
                // /cir setHome <x> <y> <z> —— 需要 4 级
                .then(Commands.literal("setHome")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                    return setHome(ctx.getSource().getServer(), x, y, z, ctx.getSource());
                                })
                            )
                        )
                    )
                )
                // /cir home —— 查看当前基地（所有玩家可用，0 级）
                .then(Commands.literal("home")
                    .requires(source -> source.hasPermission(0))
                    .executes(ctx -> {
                        MinecraftServer server = ctx.getSource().getServer();
                        HomeData data = HomeData.get(server);
                        if (!data.isInitialized()) {
                            ctx.getSource().sendFailure(
                                Component.translatable("cir.home.not_initialized")
                            );
                            return 0;
                        }
                        BlockPos home = data.getHome();
                        ctx.getSource().sendSuccess(
                            () -> Component.translatable(
                                "cir.home.current",
                                data.getDimension() == null ? "?" : data.getDimension().toString(),
                                home == null ? 0 : home.getX(),
                                home == null ? 0 : home.getY(),
                                home == null ? 0 : home.getZ()
                            ),
                            false
                        );
                        return 1;
                    })
                )
                // /cir quantum scan —— 把已加载区域里的桥批量登记，结果扔到屏幕右下角（4 级）
                .then(Commands.literal("quantum")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.literal("scan")
                        .executes(ctx -> {
                            var source = ctx.getSource();
                            var level = source.getLevel();
                            var server = source.getServer();

                            com.cirserver.createciraddition.quantum.QuantumGuard
                                .scanLoadedBridges(level);

                            Component report = com.cirserver.createciraddition.quantum.QuantumGuard
                                .buildScanReport(server, level);

                            var player = source.getPlayer();
                            if (player != null) {
                                // 第二参数 true = action bar（屏幕右下角）
                                player.sendSystemMessage(report, true);
                            } else {
                                source.sendSuccess(() -> report, false);
                            }
                            return 1;
                        })
                    )
                )
                // /cir cleanup —— 立即扫一次地（4 级）
                .then(Commands.literal("cleanup")
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> {
                        var source = ctx.getSource();
                        var server = source.getServer();
                        int cleaned = com.cirserver.createciraddition.cleanup.ItemCleaner.cleanup(server);

                        var player = source.getPlayer();
                        if (player != null) {
                            com.cirserver.createciraddition.cleanup.ItemCleaner.notifyManual(player, cleaned);
                        } else {
                            source.sendSuccess(
                                () -> Component.translatable("cir.cleanup.done", cleaned),
                                true
                            );
                        }
                        return 1;
                    })
                )
                // /cir forcecleanup —— 强制清扫（4 级）：全服倒计时后清空地面，不进垃圾桶
                .then(Commands.literal("forcecleanup")
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> {
                        startForceCleanup(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(
                            () -> Component.translatable("cir.forceclear.started"),
                            false
                        );
                        return 1;
                    })
                )
                // /cir trashbin —— 打开垃圾桶，无权限要求，所有玩家都能用
                .then(Commands.literal("trashbin")
                    .executes(ctx -> {
                        var player = ctx.getSource().getPlayer();
                        if (player == null) {
                            ctx.getSource().sendFailure(
                                Component.translatable("cir.cleanup.no_player")
                            );
                            return 0;
                        }

                        var bin = com.cirserver.createciraddition.cleanup.TrashBin.getInstance();
                        if (bin == null || bin.isExpired()) {
                            player.sendSystemMessage(
                                Component.translatable("cir.cleanup.bin_empty")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                            );
                            return 0;
                        }

                        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, p) -> new com.cirserver.createciraddition.cleanup.TrashBinMenu(id, inv, p),
                            Component.translatable("cir.cleanup.bin_title")
                        ));
                        return 1;
                    })
                )
                // /cir redstonelinkoptimization <true|false> —— 红石终端优化开关（4 级，立即生效并写盘）
                .then(Commands.literal("redstonelinkoptimization")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "enabled");
                            try {
                                CirConfig.REDSTONE_LINK_OPTIMIZATION.set(value);
                                CirConfig.save();
                                ctx.getSource().sendSuccess(
                                    () -> Component.translatable(
                                        "cir.redstonelink.toggled",
                                        Component.translatable(value
                                            ? "cir.redstonelink.on"
                                            : "cir.redstonelink.off")
                                    ),
                                    true
                                );
                            } catch (Throwable t) {
                                ctx.getSource().sendFailure(
                                    Component.translatable(
                                        "cir.redstonelink.failed", String.valueOf(t.getMessage())
                                    )
                                );
                                return 0;
                            }
                            return 1;
                        })
                    )
                )
                // /cir ae2generator [crystalresonance <true|false> | vibrationchamber <low|normal|boost>]
                // AE2 两个自发电方块的运行时调节（4 级）。改的是 AE2 自己的 ModConfigSpec，立即生效并写盘。
                .then(Commands.literal("ae2generator")
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> reportAe2Generators(ctx.getSource()))
                    .then(Commands.literal("crystalresonance")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                var source = ctx.getSource();

                                if (!com.cirserver.createciraddition.ae2.Ae2GeneratorControl.isAvailable()) {
                                    source.sendFailure(Component.translatable("cir.ae2gen.unavailable"));
                                    return 0;
                                }
                                if (!com.cirserver.createciraddition.ae2.Ae2GeneratorControl
                                    .setCrystalGeneratorEnabled(enabled)) {
                                    source.sendFailure(Component.translatable("cir.ae2gen.reflect_failed"));
                                    return 0;
                                }

                                double rate = com.cirserver.createciraddition.ae2.Ae2GeneratorControl
                                    .crystalGeneratorRate();
                                source.sendSuccess(
                                    () -> Component.translatable(
                                        "cir.ae2gen.crystal.toggled",
                                        Component.translatable(enabled ? "cir.state.on" : "cir.state.off"),
                                        formatAeNumber(rate)
                                    ),
                                    true
                                );
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("vibrationchamber")
                        .then(Commands.argument("tier", StringArgumentType.word())
                            .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                .suggest(java.util.List.of("low", "normal", "boost"), builder))
                            .executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "tier");
                                var source = ctx.getSource();
                                var tier = parseVibrationTier(raw);

                                if (tier == null) {
                                    source.sendFailure(Component.translatable("cir.ae2gen.bad_tier", raw));
                                    return 0;
                                }
                                if (!com.cirserver.createciraddition.ae2.Ae2GeneratorControl.isAvailable()) {
                                    source.sendFailure(Component.translatable("cir.ae2gen.unavailable"));
                                    return 0;
                                }
                                int refreshed = com.cirserver.createciraddition.ae2.Ae2GeneratorControl
                                    .setVibrationTier(tier, source.getServer());
                                if (refreshed < 0) {
                                    source.sendFailure(Component.translatable("cir.ae2gen.reflect_failed"));
                                    return 0;
                                }

                                double[] values = com.cirserver.createciraddition.ae2.Ae2GeneratorControl
                                    .currentVibrationValues();
                                String base = values.length >= 3 ? formatAeNumber(values[0]) : "?";
                                String low = values.length >= 3 ? formatAeNumber(values[1]) : "?";
                                String high = values.length >= 3 ? formatAeNumber(values[2]) : "?";
                                source.sendSuccess(
                                    () -> Component.translatable(
                                        "cir.ae2gen.vibration.set",
                                        Component.translatable(tierTranslationKey(tier)),
                                        base,
                                        low,
                                        high
                                    ),
                                    true
                                );
                                if (refreshed > 0) {
                                    source.sendSuccess(
                                        () -> Component.translatable("cir.ae2gen.refreshed", refreshed),
                                        false
                                    );
                                }
                                return 1;
                            })
                        )
                    )
                )
        );
    }

    /** /cir ae2generator —— 打印两个发电方块的当前状态 */
    private static int reportAe2Generators(net.minecraft.commands.CommandSourceStack source) {
        if (!com.cirserver.createciraddition.ae2.Ae2GeneratorControl.isAvailable()) {
            source.sendFailure(Component.translatable("cir.ae2gen.unavailable"));
            return 0;
        }

        boolean off = com.cirserver.createciraddition.ae2.Ae2GeneratorControl.isCrystalGeneratorOff();
        double rate = com.cirserver.createciraddition.ae2.Ae2GeneratorControl.crystalGeneratorRate();
        double[] values = com.cirserver.createciraddition.ae2.Ae2GeneratorControl.currentVibrationValues();
        var tier = com.cirserver.createciraddition.ae2.Ae2GeneratorControl.currentVibrationTier();

        source.sendSuccess(
            () -> Component.translatable("cir.ae2gen.status.title")
                .withStyle(net.minecraft.ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.translatable(
                "cir.ae2gen.status.crystal",
                Component.translatable(off ? "cir.state.off" : "cir.state.on")
                    .withStyle(off ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN),
                formatAeNumber(rate)
            ),
            false
        );
        source.sendSuccess(
            () -> Component.translatable(
                "cir.ae2gen.status.vibration",
                Component.translatable(tierTranslationKey(tier)),
                values.length >= 3 ? formatAeNumber(values[0]) : "?",
                values.length >= 3 ? formatAeNumber(values[1]) : "?",
                values.length >= 3 ? formatAeNumber(values[2]) : "?"
            ),
            false
        );
        return 1;
    }

    private static com.cirserver.createciraddition.ae2.Ae2GeneratorControl.VibrationTier parseVibrationTier(
        String raw
    ) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "low" -> com.cirserver.createciraddition.ae2.Ae2GeneratorControl.VibrationTier.LOW;
            case "normal" -> com.cirserver.createciraddition.ae2.Ae2GeneratorControl.VibrationTier.NORMAL;
            case "boost" -> com.cirserver.createciraddition.ae2.Ae2GeneratorControl.VibrationTier.BOOST;
            default -> null;
        };
    }

    private static String tierTranslationKey(
        com.cirserver.createciraddition.ae2.Ae2GeneratorControl.VibrationTier tier
    ) {
        return switch (tier) {
            case LOW -> "cir.ae2gen.tier.low";
            case NORMAL -> "cir.ae2gen.tier.normal";
            case BOOST -> "cir.ae2gen.tier.boost";
            case CUSTOM -> "cir.ae2gen.tier.custom";
        };
    }

    /** 5.0 -> "5"，1.25 -> "1.25"；固定 ROOT locale，避免某些区域把小数点点成逗号 */
    private static String formatAeNumber(double value) {
        if (Double.isNaN(value)) {
            return "?";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15D) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static int setHome(MinecraftServer server, int x, int y, int z,
                               net.minecraft.commands.CommandSourceStack source) {
        try {
            // 基地只存在于主世界，无论指令在哪里执行，都落到主世界对应坐标
            ServerLevel overworld = server.overworld();
            HomeData data = HomeData.get(server);
            BlockPos pos = new BlockPos(x, y, z);
            data.setHome(pos, overworld.dimension());

            CreateCIRAddition.LOGGER.info(
                "Create CIR Addition: Base moved by {} to {} {}",
                source.getTextName(), overworld.dimension().location(), pos
            );

            // 全服广播
            Component broadcast = Component.translatable("cir.home.broadcast", x, y, z);
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(broadcast);
            }

            // 命令执行者的反馈（广播已经覆盖了，这里给个明确的成功回执）
            source.sendSuccess(
                () -> Component.translatable("cir.home.set_success", x, y, z),
                false
            );
            return 1;
        } catch (Throwable t) {
            source.sendFailure(Component.translatable("cir.home.set_failed", String.valueOf(t.getMessage())));
            return 0;
        }
    }
}
