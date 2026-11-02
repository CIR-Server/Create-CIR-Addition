package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.config.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"com.simibubi.create.content.processing.basin.BasinBlockEntity"},
   remap = false
)
public class MixinBasinBlockEntity {
   @Unique
   private int cir$overflowCheckCounter = 0;
   @Unique
   private static final int CHECK_INTERVAL = 5;

   public MixinBasinBlockEntity() {
   }

   @Inject(
      method = {"tryClearingSpoutputOverflow"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cir$onTryClearingOverflow(CallbackInfo ci) {
      if (ModuleManager.isEnabled("create")) {
         this.cir$overflowCheckCounter++;
         if (this.cir$overflowCheckCounter >= 2147483646) {
            this.cir$overflowCheckCounter = 0;
         }

         if (this.cir$overflowCheckCounter % 5 != 0) {
            ci.cancel();
         }
      }
   }
}
