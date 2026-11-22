package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.boss.BossCombatHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Final HP damage, after absorption and before combat bookkeeping or health subtraction. */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class MixinBossFinalDamage {
    @Redirect(
        method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V")),
        at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F", ordinal = 0),
        require = 1
    )
    private float cir$guardFinalDamage(DamageContainer container, DamageSource source, float originalAmount) {
        return BossCombatHandler.finalDamage((LivingEntity)(Object)this, source, container, container.getNewDamage());
    }
}
