package com.cirserver.createciraddition.quantum;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;

/**
 * 量子环限制的总开关。
 *
 * <b>这个值只在服务器启动时读取一次。</b>
 */
public final class QuantumLimiterSwitch {

    /** null = 还没初始化 */
    private static Boolean cached = null;

    private QuantumLimiterSwitch() {
    }

    /** 服务器启动时调用一次，把配置值固化到内存 */
    public static void init() {
        try {
            cached = CirConfig.QUANTUM_LIMITER_ENABLED.get();
        } catch (Throwable t) {
            cached = false;
            CreateCIRAddition.LOGGER.warn(
                "Create CIR Addition: Failed to read quantumLimiterEnabled, defaulting to disabled", t
            );
        }
        CreateCIRAddition.LOGGER.info(
            "Create CIR Addition: Quantum limiter master switch = {} (read once at startup; restart required to change)",
            cached
        );
    }

    /** 未初始化时按关闭处理——宁可不管，也不要在启动早期误拆玩家的桥 */
    public static boolean enabled() {
        return cached != null && cached;
    }
}
