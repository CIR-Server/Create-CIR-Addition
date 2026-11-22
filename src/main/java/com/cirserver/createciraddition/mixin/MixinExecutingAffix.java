package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.boss.BossCombatHandler;
import com.cirserver.createciraddition.boss.ExecutionBlockHandler;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercept only eligible executions, before totem use, death sound and health mutation. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apotheosis.affix.effect.ExecutingAffix", remap = false)
public abstract class MixinExecutingAffix {
    @Inject(method = "doPostAttack", at = @At(value = "INVOKE",
        target = "Ldev/shadowsoffire/apotheosis/mixin/LivingEntityInvoker;callCheckTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z"),
        cancellable = true, require = 1)
    private void cir$blockBossExecution(AffixInstance instance, LivingEntity attacker, Entity target, CallbackInfo ci) {
        if (target instanceof LivingEntity living && BossCombatHandler.isProtected(living)) {
            ExecutionBlockHandler.blocked(attacker, living);
            ci.cancel();
        }
    }
}
