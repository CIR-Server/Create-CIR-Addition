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
      if (initialized) {
         return;
      }
      initialized = true;
      try {
         Class<?> allPackets = Class.forName("com.simibubi.create.AllPackets");
         for (Object constant : allPackets.getEnumConstants()) {
            if (constant.toString().equals("MOUNTED_STORAGE_SYNC")) {
               mountedStorageSyncId = ((Enum<?>)constant).ordinal();
               break;
            }
         }

         Class<?> packetClass = Class.forName("com.simibubi.create.content.contraptions.MountedStorageSyncPacket");
         mountedStorageCodec = (StreamCodec<?, ?>)packetClass.getField("STREAM_CODEC").get(null);

         for (Method m : packetClass.getDeclaredMethods()) {
            if (m.getName().equals("handle") && m.getParameterCount() == 1) {
               handleMethod = m;
               handleMethod.setAccessible(true);
               break;
            }
         }

         if (mountedStorageCodec == null) {
            throw new IllegalStateException("Failed to get MountedStorageSyncPacket codec");
         }
         CreateCIRAddition.LOGGER.info(
            "PacketRouter: Initialized. MountedStorageSyncId={}, handleMethod={}",
            mountedStorageSyncId,
            handleMethod != null ? "found" : "not found"
         );
      } catch (Throwable t) {
         CreateCIRAddition.LOGGER.error("PacketRouter: Failed to initialize", t);
         initFailed = true;
      }
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
