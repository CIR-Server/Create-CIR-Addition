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
 * <p>背景：{@code RedstoneLinkNetworkHandler.updateNetworkOf} 每次被调用都要
 * 遍历整个同频率网络，对每个节点调一次 {@code LinkBehaviour.isAlive()}：
 *
 * <pre>
 * if (this.blockEntity.isChunkUnloaded()) return false;
 * if (this.blockEntity.isRemoved())       return false;
 * return !level.isLoaded(pos) ? false : level.getBlockEntity(pos) == this.blockEntity;
 * </pre>
 *
 * <p>一次 {@code updateNetworkOf} 里 n 个节点就是 n × 4 次状态查询。而
 * {@code updateNetworkOf} 在以下三种场合都会被触发：
 * <ul>
 *   <li>区块加载 / 卸载（{@code initialize} / {@code unload}）——玩家跑过终端密集区时最凶</li>
 *   <li>信号变化（{@code transmit} → {@code notifySignalChange}）——接时钟时每 tick 一次</li>
 *   <li>换频率（{@code setFrequency}）</li>
 * </ul>
 *
 * <h2>做法</h2>
 * 把 {@code isAlive()} 的结果按 <b>游戏 tick</b> 缓存：同一个 tick 内重复问同一个
 * 终端，直接返回上一次的答案。
 *
 * <p>为什么缓存是按 tick 而不是永久：{@code isAlive} 依赖区块加载状态和方块实体
 * 是否被移除，这两个都可能变，跨 tick 缓存会读到过期结果。按 tick 缓存既保住正确性，
 * 又能把一次 {@code updateNetworkOf} 里的 n 次重复查询压成 1 次。
 *
 * <p>Create 是本模组的必需前置（mods.toml 里是 required），所以这里不做存在性判断；
 * 只受 {@code modules.create} 开关控制。
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
     * <p>{@code getWorld()} 继承自 {@code BlockEntityBehaviour}，mixin 里没法直接
     * {@code @Shadow} 继承方法，所以用反射。Method 对象按类缓存一次，后续调用只付
     * 一次 invoke 的成本——仍然比原来那 4 次状态查询便宜。
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
