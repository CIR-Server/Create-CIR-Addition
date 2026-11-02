package com.cirserver.createciraddition.quantum;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;

/**
 * 量子环限制的总开关。
 *
 * <p><b>这个值只在服务器启动时读取一次。</b>
 *
 * <p>为什么不支持热重载：量子环限制依赖一张持久化的「已登记环坐标表」，
 * 拆桥时注销、成型时登记，两边是一一对应的。如果在运行中途把开关切掉或打开，
 * 表里的记录会突然和实际方块对不上——
 * <ul>
 *   <li>关掉时：表还在，重新打开后会把老记录当成有效桥去算配额</li>
 *   <li>打开时：现有桥全部未登记，下一次区块加载会把它们当新桥判一遍，可能被拆</li>
 * </ul>
 * 所以这里刻意做成「启动读一次 + 内存缓存」，{@code /reload} 和配置热重载都改不动它。
 * 要切换必须重启。
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
