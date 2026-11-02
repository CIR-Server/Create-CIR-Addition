package com.cirserver.createciraddition.monitor;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * MSPT 负载预警。
 *
 * <p>MSPT（毫秒每 tick）直接取自 {@link MinecraftServer#getCurrentSmoothedTickTime()}，
 * 这是原版维护的平滑 tick 耗时，不需要自己累加计时。
 *
 * <p>检查节奏与广播规则：
 * <ul>
 *   <li>每 30 秒（600 tick）采样一次</li>
 *   <li>35 / 40 / 45 / 50 四档，颜色依次为白、黄、金、红</li>
 *   <li>只有在本档位且 MSPT <b>比上次采样更高</b>时才广播——下降或波动不打扰玩家</li>
 *   <li>MSPT ≥ 50 时无条件每 30 秒广播一次，直到降下来</li>
 * </ul>
 */
@EventBusSubscriber(
   modid = "create_cir_addition",
   bus = EventBusSubscriber.Bus.GAME
)
public class MsptMonitor {

    /** 采样间隔：30 秒（20 TPS 下 600 tick） */
    private static final int CHECK_INTERVAL_TICKS = 20 * 30;

    private static final float THRESHOLD_1 = 35.0F;
    private static final float THRESHOLD_2 = 40.0F;
    private static final float THRESHOLD_3 = 45.0F;
    private static final float THRESHOLD_4 = 50.0F;

    /** tick 计数器 */
    private static int tickCounter = 0;

    /** 上一次采样到的 MSPT，用于判断"是否正在上升" */
    private static float lastMspt = 0.0F;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isEnabled()) {
            // 功能关闭时把状态复位，避免重新打开后拿旧的 lastMspt 误判
            tickCounter = 0;
            lastMspt = 0.0F;
            return;
        }

        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        float mspt;
        try {
            mspt = server.getCurrentSmoothedTickTime();
        } catch (Throwable t) {
            return;
        }

        int level = levelOf(mspt);
        boolean rising = mspt > lastMspt;

        // ≥50 无条件持续广播；其余档位只在上升时广播
        if (level >= 4 || (level > 0 && rising)) {
            broadcast(server, level, mspt);
        }

        // MSPT 到红色档（≥50，也就是弹最高级警报的那一刻）→ 自动清理地面掉落物
        if (level >= 4) {
            autoCleanup(server, mspt);
        }

        lastMspt = mspt;
    }

    /**
     * MSPT 进入红色档时自动扫地。
     *
     * <p>刻意和警报同步：既然已经播了「严重超限」，顺手把地上堆积的掉落物清掉，
     * 因为实体过多的最常见的服务器卡顿来源之一。
     */
    private static void autoCleanup(MinecraftServer server, float mspt) {
        try {
            if (!CirConfig.CLEANUP_ON_HIGH_MSPT.get()) {
                return;
            }
            int cleaned = com.cirserver.createciraddition.cleanup.ItemCleaner.cleanup(server);
            if (cleaned <= 0) {
                return;
            }
            String value = String.format("%.1f", mspt);
            com.cirserver.createciraddition.cleanup.ItemCleaner.broadcastAuto(server, cleaned, value);
            CreateCIRAddition.LOGGER.info(
                "Create CIR Addition: auto-cleaned {} dropped item(s) at MSPT {}", cleaned, value
            );
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: auto cleanup failed", t);
        }
    }

    /** 把 MSPT 映射到档位 0-4 */
    private static int levelOf(float mspt) {
        if (mspt >= THRESHOLD_4) return 4;
        if (mspt >= THRESHOLD_3) return 3;
        if (mspt >= THRESHOLD_2) return 2;
        if (mspt >= THRESHOLD_1) return 1;
        return 0;
    }

    /** 档位对应聊天颜色：白 → 黄 → 金 → 红 */
    private static ChatFormatting colorOf(int level) {
        return switch (level) {
            case 1 -> ChatFormatting.WHITE;
            case 2 -> ChatFormatting.YELLOW;
            case 3 -> ChatFormatting.GOLD;
            case 4 -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    private static void broadcast(MinecraftServer server, int level, float mspt) {
        try {
            String value = String.format("%.1f", mspt);
            Component message = Component
                .translatable("cir.mspt.warn." + level, value)
                .withStyle(colorOf(level));

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: MSPT broadcast failed", t);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private static boolean isEnabled() {
        try {
            return CirConfig.MSPT_WARN_ENABLED.get();
        } catch (Throwable t) {
            // 配置文件还没加载时默认关闭，避免早期 tick 就报错
            return false;
        }
    }
}
