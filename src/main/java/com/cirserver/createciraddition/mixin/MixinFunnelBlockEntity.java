package com.cirserver.createciraddition.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(
   targets = {"com.simibubi.create.content.logistics.funnel.FunnelBlockEntity"},
   remap = false
)
public class MixinFunnelBlockEntity {
   public MixinFunnelBlockEntity() {
   }
}
