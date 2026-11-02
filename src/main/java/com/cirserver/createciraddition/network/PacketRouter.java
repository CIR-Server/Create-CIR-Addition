package com.cirserver.createciraddition.network;

import com.cirserver.createciraddition.CreateCIRAddition;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class PacketRouter {
   private static volatile boolean initialized = false;
   private static boolean initFailed = false;
   private static StreamCodec<?, ?> mountedStorageCodec = null;
   private static Method handleMethod = null;
   private static int mountedStorageSyncId = -1;

   private PacketRouter() {
   }

   public static synchronized void init() {
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
      // 00: getstatic com/cirserver/createciraddition/network/PacketRouter.initialized Z
      // 03: ifeq 07
      // 06: return
      // 07: bipush 1
      // 08: putstatic com/cirserver/createciraddition/network/PacketRouter.initialized Z
      // 0b: ldc "com.simibubi.create.AllPackets"
      // 0d: invokestatic java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
      // 10: astore 0
      // 11: aload 0
      // 12: invokevirtual java/lang/Class.getEnumConstants ()[Ljava/lang/Object;
      // 15: astore 1
      // 16: aload 1
      // 17: arraylength
      // 18: istore 2
      // 19: bipush 0
      // 1a: istore 3
      // 1b: iload 3
      // 1c: iload 2
      // 1d: if_icmpge 46
      // 20: aload 1
      // 21: iload 3
      // 22: aaload
      // 23: astore 4
      // 25: aload 4
      // 27: invokevirtual java/lang/Object.toString ()Ljava/lang/String;
      // 2a: ldc "MOUNTED_STORAGE_SYNC"
      // 2c: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 2f: ifeq 40
      // 32: aload 4
      // 34: checkcast java/lang/Enum
      // 37: invokevirtual java/lang/Enum.ordinal ()I
      // 3a: putstatic com/cirserver/createciraddition/network/PacketRouter.mountedStorageSyncId I
      // 3d: goto 46
      // 40: iinc 3 1
      // 43: goto 1b
      // 46: ldc "com.simibubi.create.content.contraptions.MountedStorageSyncPacket"
      // 48: invokestatic java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
      // 4b: astore 1
      // 4c: aload 1
      // 4d: ldc "STREAM_CODEC"
      // 4f: invokevirtual java/lang/Class.getField (Ljava/lang/String;)Ljava/lang/reflect/Field;
      // 52: astore 2
      // 53: aload 2
      // 54: aconst_null
      // 55: invokevirtual java/lang/reflect/Field.get (Ljava/lang/Object;)Ljava/lang/Object;
      // 58: checkcast net/minecraft/network/codec/StreamCodec
      // 5b: putstatic com/cirserver/createciraddition/network/PacketRouter.mountedStorageCodec Lnet/minecraft/network/codec/StreamCodec;
      // 5e: aload 1
      // 5f: invokevirtual java/lang/Class.getDeclaredMethods ()[Ljava/lang/reflect/Method;
      // 62: astore 3
      // 63: aload 3
      // 64: arraylength
      // 65: istore 4
      // 67: bipush 0
      // 68: istore 5
      // 6a: iload 5
      // 6c: iload 4
      // 6e: if_icmpge a2
      // 71: aload 3
      // 72: iload 5
      // 74: aaload
      // 75: astore 6
      // 77: aload 6
      // 79: invokevirtual java/lang/reflect/Method.getName ()Ljava/lang/String;
      // 7c: ldc "handle"
      // 7e: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 81: ifeq 9c
      // 84: aload 6
      // 86: invokevirtual java/lang/reflect/Method.getParameterCount ()I
      // 89: bipush 1
      // 8a: if_icmpne 9c
      // 8d: aload 6
      // 8f: putstatic com/cirserver/createciraddition/network/PacketRouter.handleMethod Ljava/lang/reflect/Method;
      // 92: getstatic com/cirserver/createciraddition/network/PacketRouter.handleMethod Ljava/lang/reflect/Method;
      // 95: bipush 1
      // 96: invokevirtual java/lang/reflect/Method.setAccessible (Z)V
      // 99: goto a2
      // 9c: iinc 5 1
      // 9f: goto 6a
      // a2: getstatic com/cirserver/createciraddition/network/PacketRouter.mountedStorageCodec Lnet/minecraft/network/codec/StreamCodec;
      // a5: ifnonnull b2
      // a8: new java/lang/IllegalStateException
      // ab: dup
      // ac: ldc "Failed to get MountedStorageSyncPacket codec"
      // ae: invokespecial java/lang/IllegalStateException.<init> (Ljava/lang/String;)V
      // b1: athrow
      // b2: getstatic com/cirserver/createciraddition/CreateCIRAddition.LOGGER Lorg/apache/logging/log4j/Logger;
      // b5: ldc "PacketRouter: Initialized. MountedStorageSyncId={}, handleMethod={}"
      // b7: getstatic com/cirserver/createciraddition/network/PacketRouter.mountedStorageSyncId I
      // ba: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
      // bd: getstatic com/cirserver/createciraddition/network/PacketRouter.handleMethod Ljava/lang/reflect/Method;
      // c0: ifnull c8
      // c3: ldc "found"
      // c5: goto ca
      // c8: ldc "not found"
      // ca: invokeinterface org/apache/logging/log4j/Logger.info (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V 4
      // cf: goto e2
      // d2: astore 0
      // d3: getstatic com/cirserver/createciraddition/CreateCIRAddition.LOGGER Lorg/apache/logging/log4j/Logger;
      // d6: ldc "PacketRouter: Failed to initialize"
      // d8: aload 0
      // d9: invokeinterface org/apache/logging/log4j/Logger.error (Ljava/lang/String;Ljava/lang/Throwable;)V 3
      // de: bipush 1
      // df: putstatic com/cirserver/createciraddition/network/PacketRouter.initFailed Z
      // e2: return
   }

   public static boolean route(int packetId, ByteBuf rawData, RegistryAccess registryAccess) {
      if (!initialized) {
         init();
      }

      if (initFailed) {
         CreateCIRAddition.LOGGER.warn("PacketRouter: Cannot route - initialization failed");
         return false;
      } else {
         try {
            if (packetId == mountedStorageSyncId) {
               return routeMountedStorageSync(rawData, registryAccess);
            } else {
               CreateCIRAddition.LOGGER.debug("PacketRouter: Unknown packet ID {}", packetId);
               return false;
            }
         } catch (Exception var4) {
            CreateCIRAddition.LOGGER.error("PacketRouter: Error routing packet", var4);
            return false;
         }
      }
   }

   private static boolean routeMountedStorageSync(ByteBuf rawData, RegistryAccess registryAccess) {
      if (mountedStorageCodec == null) {
         return false;
      } else {
         try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(rawData, registryAccess);
            StreamCodec<RegistryFriendlyByteBuf, Object> codec = (StreamCodec<RegistryFriendlyByteBuf, Object>)mountedStorageCodec;
            Object packet = codec.decode(buffer);
            if (packet == null) {
               CreateCIRAddition.LOGGER.warn("PacketRouter: Decoded packet is null");
               return false;
            } else if (handleMethod != null) {
               try {
                  handleMethod.invoke(packet, null);
                  CreateCIRAddition.LOGGER.debug("PacketRouter: Successfully handled MountedStorageSyncPacket");
                  return true;
               } catch (Exception var6) {
                  CreateCIRAddition.LOGGER.debug("PacketRouter: handle() call failed, packet was decoded but not processed: {}", var6.getMessage());
                  return true;
               }
            } else {
               CreateCIRAddition.LOGGER.debug("PacketRouter: Decoded MountedStorageSyncPacket (no handle method available)");
               return true;
            }
         } catch (Exception var7) {
            CreateCIRAddition.LOGGER.error("PacketRouter: Failed to route MountedStorageSyncPacket", var7);
            return false;
         }
      }
   }

   public static int getMountedStorageSyncId() {
      if (!initialized) {
         init();
      }

      return mountedStorageSyncId;
   }
}
