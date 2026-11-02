package com.cirserver.createciraddition.home;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 基地系统的初始化与命令注册。
 *
 * <p>首次进入新地图时自动把出生点设为基地；之后要改位置用
 * {@code /cir setHome <x> <y> <z>}。
 *
 * <p>基地在任何时候都会初始化（不依赖 AE2），但只有 AE2 装了才会被量子环逻辑用到。
 */
@EventBusSubscriber(
   modid = "create_cir_addition",
   bus = EventBusSubscriber.Bus.GAME
)
public class HomeEvents {

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
                // /cir home —— 查看当前基地（4 级）
                .then(Commands.literal("home")
                    .requires(source -> source.hasPermission(4))
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
        );
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
