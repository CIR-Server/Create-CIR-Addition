package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.config.ModuleManager;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"com.simibubi.create.foundation.blockEntity.SmartBlockEntity"},
   remap = false
)
public abstract class MixinSmartBlockEntity {
   @Unique
   private int cir$syncCounter = 0;
   @Unique
   private static final int SYNC_INTERVAL = 3;
   @Unique
   private static Field cir$lazyTickRateField = null;
   @Unique
   private static boolean cir$lazyTickFieldFailed = false;
   @Unique
   private int cir$distanceCheckCounter = 0;
   @Unique
   private static final int DISTANCE_CHECK_INTERVAL = 100;
   @Unique
   private static final double FAR_DISTANCE_SQ = 4096.0;
   @Unique
   private static final int NEAR_LAZY_TICK_RATE = 10;
   @Unique
   private static final int FAR_LAZY_TICK_RATE = 40;

   public MixinSmartBlockEntity() {
   }

   @Inject(
      method = {"sendData"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cir$onSendData(CallbackInfo ci) {
      if (ModuleManager.isEnabled("create")) {
         this.cir$syncCounter++;
         if (this.cir$syncCounter >= 2147483646) {
            this.cir$syncCounter = 0;
         }

         if (this.cir$syncCounter % 3 != 0) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"notifyUpdate"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cir$onNotifyUpdate(CallbackInfo ci) {
      if (ModuleManager.isEnabled("create")) {
         if (this.cir$syncCounter % 3 != 0) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"lazyTick"},
      at = {@At("HEAD")},
      require = 0
   )
   private void cir$onLazyTick(CallbackInfo ci) {
      if (ModuleManager.isEnabled("create") && !cir$lazyTickFieldFailed) {
         this.cir$distanceCheckCounter++;
         if (this.cir$distanceCheckCounter >= 2147483646) {
            this.cir$distanceCheckCounter = 0;
         }

         if (this.cir$distanceCheckCounter % 100 == 0) {
            if (cir$lazyTickRateField == null) {
               try {
                  Class<?> smartBEClass = Class.forName("com.simibubi.create.foundation.blockEntity.SmartBlockEntity");
                  cir$lazyTickRateField = smartBEClass.getDeclaredField("lazyTickRate");
                  cir$lazyTickRateField.setAccessible(true);
               } catch (Exception var16) {
                  cir$lazyTickFieldFailed = true;
                  return;
               }
            }

            try {
               BlockEntity be = (BlockEntity)(Object)this;
               if (be.getLevel() == null || be.getLevel().isClientSide()) {
                  return;
               }

               ServerLevel level = (ServerLevel)be.getLevel();
               double minDistSq = Double.MAX_VALUE;

               for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                  if (player.level() == level) {
                     double dx = player.getX() - (double)be.getBlockPos().getX();
                     double dy = player.getY() - (double)be.getBlockPos().getY();
                     double dz = player.getZ() - (double)be.getBlockPos().getZ();
                     double distSq = dx * dx + dy * dy + dz * dz;
                     if (distSq < minDistSq) {
                        minDistSq = distSq;
                     }
                  }
               }

               int targetRate = minDistSq > 4096.0 ? 40 : 10;
               cir$lazyTickRateField.setInt(this, targetRate);
            } catch (Exception var17) {
            }
         }
      }
   }
}
