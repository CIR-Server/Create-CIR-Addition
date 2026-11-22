package com.cirserver.createciraddition.compat;

import com.cirserver.createciraddition.config.CirConfig;

/**
 * CEI 的宝石切割升阶在 Java 里硬编码，单独删神化配方拦不住。
 * 这里统一约束 CEI 的加工入口，具体哪些纯度放行由配置决定
 * （{@code gemCutAllowCracked} … {@code gemCutAllowPerfect}）。
 *
 * <p>纯度顺序（Apotheosis Purity 枚举）：CRACKED=0, CHIPPED=1, FLAWED=2,
 * NORMAL=3, FLAWLESS=4, PERFECT=5。判断的是「输入纯度」。
 *
 * <p>配置只在启动时读取，改完要重启游戏。
 */
public final class PolicyRules {
    private PolicyRules() {
    }

    /**
     * 输入纯度是否允许用切割机升阶。
     *
     * @param inputPurityOrdinal 输入宝石的纯度序号（0-5）
     * @return true 放行，false 拒绝
     */
    public static boolean allowLegacyCut(int inputPurityOrdinal) {
        try {
            return switch (inputPurityOrdinal) {
                case 0 -> CirConfig.GEM_CUT_ALLOW_CRACKED.get();
                case 1 -> CirConfig.GEM_CUT_ALLOW_CHIPPED.get();
                case 2 -> CirConfig.GEM_CUT_ALLOW_FLAWED.get();
                case 3 -> CirConfig.GEM_CUT_ALLOW_NORMAL.get();
                case 4 -> CirConfig.GEM_CUT_ALLOW_FLAWLESS.get();
                case 5 -> CirConfig.GEM_CUT_ALLOW_PERFECT.get();
                default -> false;
            };
        } catch (Throwable configNotLoaded) {
            // 配置还没加载时保守处理：只放行最低阶，其余按禁止算
            return inputPurityOrdinal == 0;
        }
    }
}
