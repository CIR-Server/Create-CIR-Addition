package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.config.ModuleManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"com.simibubi.create.content.logistics.box.PackageEntity"},
   remap = false
)
public abstract class MixinPackageEntity extends Entity {
   @Unique
   private int cir$tickCounter = 0;
   @Unique
   private int cir$lastSyncTick = 0;

   private MixinPackageEntity() {
      super(null, null);
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cir$onTick(CallbackInfo ci) {
      if (ModuleManager.isEnabled("create")) {
         this.cir$tickCounter++;
         if (this.cir$tickCounter >= 2147483646) {
            this.cir$tickCounter = 0;
            this.cir$lastSyncTick = 0;
         }

         int syncInterval = this.cir$calculateSyncInterval();
         if (this.cir$tickCounter - this.cir$lastSyncTick >= syncInterval) {
            this.cir$lastSyncTick = this.cir$tickCounter;
         }
      }
   }

   @Unique
   private int cir$calculateSyncInterval() {
      if (this.level() != null && !this.level().isClientSide()) {
         MinecraftServer server = this.level().getServer();
         if (server == null) {
            return 2;
         } else {
            double minDistSq = Double.MAX_VALUE;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               if (player.level() == this.level()) {
                  double distSq = this.distanceToSqr(player);
                  if (distSq < minDistSq) {
                     minDistSq = distSq;
                  }
               }
            }

            if (minDistSq <= 256.0) {
               return 2;
            } else if (minDistSq <= 1024.0) {
               return 5;
            } else {
               return minDistSq <= 4096.0 ? 10 : 20;
            }
         }
      } else {
         return 2;
      }
   }
}
