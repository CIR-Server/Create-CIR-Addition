package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.config.CirConfig;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 优化无线红石信号终端（{@code create:redstone_link}）的存活判定。
 *
 * 背景：{@code RedstoneLinkNetworkHandler.updateNetworkOf} 每次被调用都要
 */
@Mixin(
    targets = {"com.simibubi.create.content.redstone.link.LinkBehaviour"},
    remap = false
)
public abstract class MixinLinkBehaviour {

    /** 上次缓存对应的游戏 tick */
    @Unique
    private long cir$aliveTick = Long.MIN_VALUE;

    /** 上次缓存的结果 */
    @Unique
    private boolean cir$aliveCached = true;

    /** 反射拿到的 getWorld()，所有实例共用 */
    @Unique
    private static Method cir$getWorld = null;

    /** 反射失败后不再重试，避免每次都抛异常 */
    @Unique
    private static boolean cir$getWorldFailed = false;

    /** 同一个 tick 内直接返回缓存值 */
    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true, require = 0)
    private void cir$useCachedAlive(CallbackInfoReturnable<Boolean> cir) {
        if (!CirConfig.isRedstoneLinkOptimizationEnabled()) {
            return;
        }
        try {
            Level level = cir$world(this);
            if (level == null) {
                return;
            }
            if (level.getGameTime() == this.cir$aliveTick) {
                cir.setReturnValue(this.cir$aliveCached);
            }
        } catch (Throwable ignored) {
            // 任何异常都退回原逻辑，绝不影响功能
        }
    }

    /** 记录本次结果，供同一 tick 内的后续查询使用 */
    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true, require = 0)
    private void cir$storeAlive(CallbackInfoReturnable<Boolean> cir) {
        if (!CirConfig.isRedstoneLinkOptimizationEnabled()) {
            return;
        }
        try {
            Level level = cir$world(this);
            if (level == null) {
                return;
            }
            this.cir$aliveTick = level.getGameTime();
            this.cir$aliveCached = cir.getReturnValue();
        } catch (Throwable ignored) {
            // 同上
        }
    }

    /**
 * 拿到行为所属的世界。
 *
 * {@code getWorld()} 继承自 {@code BlockEntityBehaviour}，mixin 里没法直接
 */
    @Unique
    private static Level cir$world(Object self) {
        if (cir$getWorldFailed) {
            return null;
        }
        try {
            if (cir$getWorld == null) {
                cir$getWorld = self.getClass().getMethod("getWorld");
                cir$getWorld.setAccessible(true);
            }
            Object world = cir$getWorld.invoke(self);
            return world instanceof Level level ? level : null;
        } catch (Throwable t) {
            cir$getWorldFailed = true;
            return null;
        }
    }
}
