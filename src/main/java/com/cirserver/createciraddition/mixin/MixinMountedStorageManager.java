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
import java.lang.reflect.Field;
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
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.NullPointerException: Cannot invoke "org.jetbrains.java.decompiler.struct.gen.VarType.isGeneric()" because "newRet" is null
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent.getInferredExprType(InvocationExprent.java:634)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.toJava(DoStatement.java:146)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:833)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:833)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement.toJava(CatchStatement.java:166)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:833)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:833)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement.toJava(IfStatement.java:241)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
      //   at org.jetbrains.java.decompiler.main.ClassWriter.writeMethod(ClassWriter.java:1283)
      //
      // Bytecode:
      // 000: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$reflectionInitialized Z
      // 003: ifeq 007
      // 006: return
      // 007: bipush 1
      // 008: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$reflectionInitialized Z
      // 00b: ldc "com.simibubi.create.content.contraptions.MountedStorageManager"
      // 00d: invokestatic java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
      // 010: astore 0
      // 011: aload 0
      // 012: ldc "syncCooldown"
      // 014: invokevirtual java/lang/Class.getDeclaredField (Ljava/lang/String;)Ljava/lang/reflect/Field;
      // 017: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$syncCooldownField Ljava/lang/reflect/Field;
      // 01a: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$syncCooldownField Ljava/lang/reflect/Field;
      // 01d: bipush 1
      // 01e: invokevirtual java/lang/reflect/Field.setAccessible (Z)V
      // 021: ldc "com.simibubi.create.AllPackets"
      // 023: invokestatic java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
      // 026: astore 1
      // 027: aload 1
      // 028: invokevirtual java/lang/Class.getEnumConstants ()[Ljava/lang/Object;
      // 02b: astore 2
      // 02c: aload 2
      // 02d: arraylength
      // 02e: istore 3
      // 02f: bipush 0
      // 030: istore 4
      // 032: iload 4
      // 034: iload 3
      // 035: if_icmpge 05f
      // 038: aload 2
      // 039: iload 4
      // 03b: aaload
      // 03c: astore 5
      // 03e: aload 5
      // 040: invokevirtual java/lang/Object.toString ()Ljava/lang/String;
      // 043: ldc "MOUNTED_STORAGE_SYNC"
      // 045: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 048: ifeq 059
      // 04b: aload 5
      // 04d: checkcast java/lang/Enum
      // 050: invokevirtual java/lang/Enum.ordinal ()I
      // 053: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$mountedStorageSyncId I
      // 056: goto 05f
      // 059: iinc 4 1
      // 05c: goto 032
      // 05f: ldc "net.createmod.catnip.platform.CatnipServices"
      // 061: invokestatic java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
      // 064: astore 2
      // 065: aload 2
      // 066: ldc "NETWORK"
      // 068: invokevirtual java/lang/Class.getField (Ljava/lang/String;)Ljava/lang/reflect/Field;
      // 06b: astore 3
      // 06c: aload 3
      // 06d: aconst_null
      // 06e: invokevirtual java/lang/reflect/Field.get (Ljava/lang/Object;)Ljava/lang/Object;
      // 071: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$networkService Ljava/lang/Object;
      // 074: aconst_null
      // 075: astore 4
      // 077: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$networkService Ljava/lang/Object;
      // 07a: invokevirtual java/lang/Object.getClass ()Ljava/lang/Class;
      // 07d: invokevirtual java/lang/Class.getMethods ()[Ljava/lang/reflect/Method;
      // 080: astore 5
      // 082: aload 5
      // 084: arraylength
      // 085: istore 6
      // 087: bipush 0
      // 088: istore 7
      // 08a: iload 7
      // 08c: iload 6
      // 08e: if_icmpge 0bb
      // 091: aload 5
      // 093: iload 7
      // 095: aaload
      // 096: astore 8
      // 098: aload 8
      // 09a: invokevirtual java/lang/reflect/Method.getName ()Ljava/lang/String;
      // 09d: ldc "sendToClientsTrackingEntity"
      // 09f: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 0a2: ifeq 0b5
      // 0a5: aload 8
      // 0a7: invokevirtual java/lang/reflect/Method.getParameterCount ()I
      // 0aa: bipush 2
      // 0ab: if_icmpne 0b5
      // 0ae: aload 8
      // 0b0: astore 4
      // 0b2: goto 0bb
      // 0b5: iinc 7 1
      // 0b8: goto 08a
      // 0bb: aload 4
      // 0bd: ifnull 0cb
      // 0c0: invokestatic java/lang/invoke/MethodHandles.lookup ()Ljava/lang/invoke/MethodHandles$Lookup;
      // 0c3: aload 4
      // 0c5: invokevirtual java/lang/invoke/MethodHandles$Lookup.unreflect (Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;
      // 0c8: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$sendHandle Ljava/lang/invoke/MethodHandle;
      // 0cb: ldc "com.simibubi.create.content.contraptions.MountedStorageSyncPacket"
      // 0cd: invokestatic java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
      // 0d0: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$packetClass Ljava/lang/Class;
      // 0d3: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$packetClass Ljava/lang/Class;
      // 0d6: ldc "STREAM_CODEC"
      // 0d8: invokevirtual java/lang/Class.getField (Ljava/lang/String;)Ljava/lang/reflect/Field;
      // 0db: astore 5
      // 0dd: aload 5
      // 0df: aconst_null
      // 0e0: invokevirtual java/lang/reflect/Field.get (Ljava/lang/Object;)Ljava/lang/Object;
      // 0e3: checkcast net/minecraft/network/codec/StreamCodec
      // 0e6: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$packetCodec Lnet/minecraft/network/codec/StreamCodec;
      // 0e9: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$mountedStorageSyncId I
      // 0ec: iflt 101
      // 0ef: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$networkService Ljava/lang/Object;
      // 0f2: ifnull 101
      // 0f5: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$sendHandle Ljava/lang/invoke/MethodHandle;
      // 0f8: ifnull 101
      // 0fb: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$packetCodec Lnet/minecraft/network/codec/StreamCodec;
      // 0fe: ifnonnull 10b
      // 101: new java/lang/IllegalStateException
      // 104: dup
      // 105: ldc "Incomplete reflection initialization"
      // 107: invokespecial java/lang/IllegalStateException.<init> (Ljava/lang/String;)V
      // 10a: athrow
      // 10b: getstatic com/cirserver/createciraddition/CreateCIRAddition.LOGGER Lorg/apache/logging/log4j/Logger;
      // 10e: ldc "Create CIR Addition: MountedStorage Mixin ready. PacketID={}, syncCooldown field=OK"
      // 110: getstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$mountedStorageSyncId I
      // 113: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
      // 116: invokeinterface org/apache/logging/log4j/Logger.info (Ljava/lang/String;Ljava/lang/Object;)V 3
      // 11b: goto 12e
      // 11e: astore 0
      // 11f: getstatic com/cirserver/createciraddition/CreateCIRAddition.LOGGER Lorg/apache/logging/log4j/Logger;
      // 122: ldc "Create CIR Addition: MountedStorage reflection init failed"
      // 124: aload 0
      // 125: invokeinterface org/apache/logging/log4j/Logger.error (Ljava/lang/String;Ljava/lang/Throwable;)V 3
      // 12a: bipush 1
      // 12b: putstatic com/cirserver/createciraddition/mixin/MixinMountedStorageManager.cir$reflectionFailed Z
      // 12e: return
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
