package com.cirserver.createciraddition.mixin;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修 Create 6.0.10 动力冲压机（Mechanical Press）的一个客户端崩溃：
 *
 * MechanicalPressBlockEntity 把「活的 ItemStack 引用」塞进 PressingBehaviour.particleItems，
 * 之后物品被消耗，同一个对象变成 count=0。同步给客户端时写出 count:0，
 * 客户端 ItemStack.parseOptional 解析成 EMPTY，spawnParticles 里
 * new ItemParticleOption(ParticleTypes.ITEM, EMPTY) 抛
 * IllegalArgumentException: Empty stacks are not allowed → 协议错误 → 掉线。
 *
 * 这里在遍历前把空栈摘掉，客户端就不会再炸。
 */
@Pseudo
@Mixin(
   targets = {"com.simibubi.create.content.kinetics.press.PressingBehaviour"},
   remap = false
)
public abstract class MixinPressingBehaviour {
   @Shadow
   public List<ItemStack> particleItems;

   @Inject(
      method = {"spawnParticles"},
      at = {@At("HEAD")},
      require = 0
   )
   private void cir$stripEmptyParticles(CallbackInfo ci) {
      if (this.particleItems != null && !this.particleItems.isEmpty()) {
         this.particleItems.removeIf(stack -> stack == null || stack.isEmpty());
      }
   }
}
