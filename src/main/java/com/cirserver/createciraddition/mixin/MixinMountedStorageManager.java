package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import com.cirserver.createciraddition.config.ModuleManager;
import com.cirserver.createciraddition.network.CompressedPayload;
import com.cirserver.createciraddition.network.CompressionUtil;
import com.cirserver.createciraddition.network.DuplicatePacketFilter;
import com.cirserver.createciraddition.stats.CompressionStats;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"com.simibubi.create.content.contraptions.MountedStorageManager"},
   remap = false
)
public class MixinMountedStorageManager {
   @Unique
   private static volatile boolean cir$reflectionInitialized = false;
   @Unique
   private static boolean cir$reflectionFailed = false;
   @Unique
   private static int cir$mountedStorageSyncId = -1;
   @Unique
   private static Object cir$networkService = null;
   @Unique
   private static MethodHandle cir$sendHandle = null;
   @Unique
   private static StreamCodec<?, ?> cir$packetCodec = null;
   @Unique
   private static Class<?> cir$packetClass = null;
   @Unique
   private static Field cir$syncCooldownField = null;

   public MixinMountedStorageManager() {
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      require = 0
   )
   private void cir$onTickHead(CallbackInfo ci) {
      if (!cir$reflectionInitialized) {
         cir$initAllReflection();
      }

      if (ModuleManager.isEnabled("create") && cir$syncCooldownField != null) {
         try {
            int currentCooldown = cir$syncCooldownField.getInt(this);
            int targetCooldown = cir$getTargetSyncCooldown();
            if (currentCooldown > 0 && currentCooldown <= 8) {
               cir$syncCooldownField.setInt(this, targetCooldown);
            }
         } catch (Exception var4) {
         }
      }
   }

   @Unique
   private static int cir$getTargetSyncCooldown() {
      try {
         return (Integer)CirConfig.MOUNTED_STORAGE_SYNC_COOLDOWN.get();
      } catch (Exception var1) {
         return 20;
      }
   }

   @Unique
   private static synchronized void cir$initAllReflection() {
      if (cir$reflectionInitialized) {
         return;
      }
      cir$reflectionInitialized = true;
      try {
         Class<?> manager = Class.forName("com.simibubi.create.content.contraptions.MountedStorageManager");
         cir$syncCooldownField = manager.getDeclaredField("syncCooldown");
         cir$syncCooldownField.setAccessible(true);

         Class<?> allPackets = Class.forName("com.simibubi.create.AllPackets");
         for (Object constant : allPackets.getEnumConstants()) {
            if (constant.toString().equals("MOUNTED_STORAGE_SYNC")) {
               cir$mountedStorageSyncId = ((Enum<?>)constant).ordinal();
               break;
            }
         }

         Class<?> catnip = Class.forName("net.createmod.catnip.platform.CatnipServices");
         cir$networkService = catnip.getField("NETWORK").get(null);

         Method sendMethod = null;
         for (Method m : cir$networkService.getClass().getMethods()) {
            if (m.getName().equals("sendToClientsTrackingEntity") && m.getParameterCount() == 2) {
               sendMethod = m;
               break;
            }
         }
         if (sendMethod != null) {
            cir$sendHandle = MethodHandles.lookup().unreflect(sendMethod);
         }

         cir$packetClass = Class.forName("com.simibubi.create.content.contraptions.MountedStorageSyncPacket");
         cir$packetCodec = (StreamCodec<?, ?>)cir$packetClass.getField("STREAM_CODEC").get(null);

         if (cir$mountedStorageSyncId < 0 || cir$networkService == null || cir$sendHandle == null || cir$packetCodec == null) {
            throw new IllegalStateException("Incomplete reflection initialization");
         }
         CreateCIRAddition.LOGGER.info(
            "Create CIR Addition: MountedStorage Mixin ready. PacketID={}, syncCooldown field=OK",
            cir$mountedStorageSyncId
         );
      } catch (Throwable t) {
         CreateCIRAddition.LOGGER.error("Create CIR Addition: MountedStorage reflection init failed", t);
         cir$reflectionFailed = true;
      }
   }

   @Unique
   private static int cir$getPacketId() {
      if (!cir$reflectionInitialized) {
         cir$initAllReflection();
      }

      return cir$mountedStorageSyncId;
   }

   @Unique
   private static void cir$compressAndSend(Object entity, Object packet, Object registryAccess) {
      if (!ModuleManager.isEnabled("create")) {
         cir$sendViaReflection(entity, packet);
      } else if (!cir$reflectionFailed && cir$packetCodec != null) {
         ByteBuf buffer = null;
         ByteBuf compressedData = null;

         try {
            buffer = PooledByteBufAllocator.DEFAULT.buffer();
            RegistryFriendlyByteBuf tempBuffer = new RegistryFriendlyByteBuf(buffer, (RegistryAccess)registryAccess);
            StreamCodec<RegistryFriendlyByteBuf, Object> codec = (StreamCodec<RegistryFriendlyByteBuf, Object>)cir$packetCodec;
            codec.encode(tempBuffer, packet);
            int originalSize = tempBuffer.readableBytes();
            byte[] data = new byte[originalSize];
            tempBuffer.getBytes(0, data);
            int contentHash = DuplicatePacketFilter.computeHash(data);
            if (DuplicatePacketFilter.isDuplicate(packet, contentHash)) {
               CreateCIRAddition.LOGGER.debug("Create CIR Addition: Filtered duplicate packet");
               return;
            }

            if (!CompressionUtil.shouldCompress(originalSize)) {
               cir$sendViaReflection(entity, packet);
               return;
            }

            byte mode = 1;
            long compressStart = System.nanoTime();
            compressedData = CompressionUtil.compress(tempBuffer);
            compressedData.markReaderIndex();
            compressedData.readInt();
            int compressedSize = compressedData.readableBytes();
            compressedData.resetReaderIndex();
            if (!((float)compressedSize >= (float)originalSize * 0.9F)) {
               long compressDuration = System.nanoTime() - compressStart;
               CompressionStats.get().recordCompression(originalSize, compressedSize, compressDuration, mode);
               CompressedPayload wrapper = new CompressedPayload(cir$getPacketId(), mode, compressedData, originalSize);
               cir$sendViaReflection(entity, wrapper);
               compressedData = null;
               return;
            }

            compressedData.release();
            compressedData = null;
            cir$sendViaReflection(entity, packet);
         } catch (Exception var20) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: Compression failed", var20);
            cir$sendViaReflection(entity, packet);
            return;
         } finally {
            if (buffer != null && buffer.refCnt() > 0) {
               buffer.release();
            }

            if (compressedData != null && compressedData.refCnt() > 0) {
               compressedData.release();
            }
         }
      } else {
         cir$sendViaReflection(entity, packet);
      }
   }

   @Unique
   private static void cir$sendViaReflection(Object entity, Object packet) {
      if (cir$sendHandle != null && cir$networkService != null) {
         try {
            cir$sendHandle.invoke((Object)cir$networkService, (Object)entity, (Object)packet);
         } catch (Throwable var3) {
            CreateCIRAddition.LOGGER.error("Create CIR Addition: Failed to send packet", var3);
         }
      }
   }
}
