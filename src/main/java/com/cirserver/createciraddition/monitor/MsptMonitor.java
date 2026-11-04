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
 * MSPT 负载预警（带复检）。
 *
 * <h2>为什么需要复检</h2>
 */
@EventBusSubscriber(
   modid = "create_cir_addition",
   bus = EventBusSubscriber.Bus.GAME
)
public class MsptMonitor {

    /** 采样间隔：30 秒（20 TPS 下 600 tick） */
    private static final int CHECK_INTERVAL_TICKS = 20 * 30;

    /** 复检延迟：3 秒（60 tick） */
    private static final int VERIFY_DELAY_TICKS = 20 * 3;

    private static final float THRESHOLD_1 = 40.0F;
    private static final float THRESHOLD_2 = 45.0F;
    private static final float THRESHOLD_3 = 50.0F;
    private static final float THRESHOLD_4 = 70.0F;

    /** 距离下一次第一次采样的 tick 计数 */
    private static int tickCounter = 0;

    /** 是否处于「等 3 秒复检」状态 */
    private static boolean verifying = false;

    /** 复检倒计时 */
    private static int verifyTicksLeft = 0;

    /** 第一次采样时读到的档位，复检判定用 */
    private static int firstLevel = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isEnabled()) {
            reset();
            return;
        }

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        // ---------- 阶段 2：复检 ----------
        if (verifying) {
            if (--verifyTicksLeft > 0) {
                return;
            }
            verifying = false;

            float mspt = readMspt(server);
            if (mspt < 0.0F) {
                firstLevel = 0;
                return;
            }

            int level = levelOf(mspt);

            // 同一档或更高才报，掉档就静默
            if (level > 0 && level >= firstLevel) {
                broadcast(server, level, mspt);
                if (level >= 4) {
                    autoCleanup(server, mspt);
                }
            }

            firstLevel = 0;
            // 本 tick 不再做第一次采样，保证两个阶段不重叠
            return;
        }

        // ---------- 阶段 1：每 30 秒采样 ----------
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        float mspt = readMspt(server);
        if (mspt < 0.0F) {
            return;
        }

        int level = levelOf(mspt);
        if (level <= 0) {
            // 没到第一条线，什么都不做
            return;
        }

        // 到线了，先不报，安排 3 秒后复检
        verifying = true;
        verifyTicksLeft = VERIFY_DELAY_TICKS;
        firstLevel = level;
    }

    /** 读 MSPT，失败返回 -1 表示"读不到"（区别于真实的 0） */
    private static float readMspt(MinecraftServer server) {
        try {
            return server.getCurrentSmoothedTickTime();
        } catch (Throwable t) {
            return -1.0F;
        }
    }

    /** 功能关闭时复位，避免重新打开后带着旧的待验证状态 */
    private static void reset() {
        tickCounter = 0;
        verifying = false;
        verifyTicksLeft = 0;
        firstLevel = 0;
    }

    /**
 * MSPT 进入红色档时自动扫地。
 *
 * 和警报同步：既然已经确认是持续性高负载，顺手把地上堆积的掉落物清掉，
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
