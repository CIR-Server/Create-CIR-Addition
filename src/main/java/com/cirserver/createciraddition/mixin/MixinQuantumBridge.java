package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.quantum.QuantumGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 钩住 AE2 的量子桥状态更新，在结构刚成型时做数量判定。
 *
 * <p>目标类是 AE2 的 {@code QuantumBridgeBlockEntity}，这里用字符串写类名而不是
 * 直接 import，避免对 AE2 产生编译期依赖。真正的加载开关在
 * {@link CirMixinPlugin#shouldApplyMixin} 里按 AE2 是否安装决定。
 *
 * <p>挂钩的方法是 {@code updateStatus(QuantumCluster, byte, boolean)}——它是
 * {@code constructed} 字段唯一的写入点，也就是「成型 / 解散」的唯一状态变更处。
 * 注入方法刻意不接收原方法参数，只带 CallbackInfo，这样参数类型里就不会出现
 * AE2 的类。
 */
@Mixin(targets = "appeng.blockentity.qnb.QuantumBridgeBlockEntity", remap = false)
public class MixinQuantumBridge {

    /** 上一次已知的成型状态，用于识别「刚刚成型」这一刻 */
    @Unique
    private boolean cir$wasFormed = false;

    /** 注入是否被成功调用的计数，用于诊断 */
    @Unique
    private int cir$hitCount = 0;

    @Inject(method = "updateStatus", at = @At("TAIL"), require = 1)
    private void cir$onStatusUpdate(CallbackInfo ci) {
        try {
            cir$hitCount++;
            // 前几次调用打日志，确认注入真的生效
            if (cir$hitCount <= 5) {
                com.cirserver.createciraddition.CreateCIRAddition.LOGGER.info(
                    "Create CIR Addition: updateStatus hit #{} on {}", cir$hitCount, this
                );
            }
            if (!QuantumGuard.isAe2Present()) {
                return;
            }
            boolean before = cir$wasFormed;
            cir$wasFormed = QuantumGuard.onStatusUpdate((Object) this, cir$wasFormed);
            // 从「成型」变成「解散」：注销登记，把配额还回去
            if (before && !cir$wasFormed) {
                QuantumGuard.onUnformed((Object) this);
            }
        } catch (Throwable t) {
            com.cirserver.createciraddition.CreateCIRAddition.LOGGER.warn(
                "Create CIR Addition: status update handler failed", t
            );
        }
    }
}
