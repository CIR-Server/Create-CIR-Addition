package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.compat.PerfectItemNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把指定物品的悬浮名改成 Apotheosis 的 PERFECT 彩虹色。
 *
 * <p>只改返回值，不动物品本身；lang 翻译照常，颜色是运行时叠加的样式。
 * 目标物品由 {@link PerfectItemNames#isTarget} 判定，模组/物品不在时自然不命中。
 */
@Mixin(ItemStack.class)
public abstract class MixinPerfectItemName {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true, require = 0)
    private void cir$perfectRainbowName(CallbackInfoReturnable<Component> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!PerfectItemNames.isTarget(self)) {
            return;
        }
        Component original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        cir.setReturnValue(PerfectItemNames.apply(original));
    }
}
