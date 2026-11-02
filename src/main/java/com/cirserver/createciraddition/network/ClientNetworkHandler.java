package com.cirserver.createciraddition.network;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.ModuleManager;
import io.netty.buffer.ByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientNetworkHandler {
   public ClientNetworkHandler() {
   }

   public static void handle(CompressedPayload payload, IPayloadContext context) {
      if (ModuleManager.isEnabled("network")) {
         context.enqueueWork(() -> {
            try {
               ByteBuf compressedData = payload.compressedData();
               byte mode = payload.mode();
               ByteBuf decompressed;
               if (mode == 1) {
                  decompressed = CompressionUtil.decompress(compressedData, (byte)1);
               } else {
                  if (mode != 2) {
                     CreateCIRAddition.LOGGER.warn("Create CIR Addition: Unknown compression mode: {}", mode);
                     return;
                  }

                  decompressed = CompressionUtil.decompress(compressedData, (byte)2);
               }

               boolean handled = PacketRouter.route(payload.originalPacketId(), decompressed, context.player().registryAccess());
               if (!handled) {
                  CreateCIRAddition.LOGGER.debug("Create CIR Addition: Packet routing failed for ID {}", payload.originalPacketId());
               }

               if (decompressed.refCnt() > 0) {
                  decompressed.release();
               }
            } catch (Exception var6) {
               CreateCIRAddition.LOGGER.error("Create CIR Addition: Error handling compressed packet", var6);
            }
         });
      }
   }
}
