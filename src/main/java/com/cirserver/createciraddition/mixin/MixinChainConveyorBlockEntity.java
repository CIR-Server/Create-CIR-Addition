package com.cirserver.createciraddition.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(
   targets = {"com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity"},
   remap = false
)
public class MixinChainConveyorBlockEntity {
   public MixinChainConveyorBlockEntity() {
   }
}
