package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.compat.PolicyRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 限制 CEI 宝石切割器：只允许低纯度升阶。
 * 用字符串 target + @Coerce 避免直接引用可选模组的类型。
 */
@Pseudo
@Mixin(
    targets = "plus.dragons.createenchantmentindustry.integration.apotheosis.common.processing.socket.gem.gemCutter.GemCutting",
    remap = false
)
public abstract class MixinCeiGemCutting {
    @Inject(
        method = "canCut(Ldev/shadowsoffire/apotheosis/socket/gem/Purity;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 1
    )
    private static void cir$restrictLegacyCut(@Coerce Object purity, CallbackInfoReturnable<Boolean> cir) {
        if (!(purity instanceof Enum<?> value) || !PolicyRules.allowLegacyCut(value.ordinal())) {
            cir.setReturnValue(false);
        }
    }
}
