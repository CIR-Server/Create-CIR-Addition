package com.cirserver.createciraddition.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record CompressedPayload(int originalPacketId, byte mode, ByteBuf compressedData, int originalSize) implements CustomPacketPayload {
   public static final Type<CompressedPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "compressed_packet"));
   public static final StreamCodec<FriendlyByteBuf, CompressedPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
      buffer.writeVarInt(payload.originalPacketId);
      buffer.writeByte(payload.mode);
      buffer.writeVarInt(payload.originalSize);
      byte[] bytes = new byte[payload.compressedData.readableBytes()];
      payload.compressedData.getBytes(0, bytes);
      buffer.writeByteArray(bytes);
   }, buffer -> {
      int originalId = buffer.readVarInt();
      byte mode = buffer.readByte();
      int originalSize = buffer.readVarInt();
      byte[] bytes = buffer.readByteArray();
      ByteBuf data = Unpooled.wrappedBuffer(bytes);
      return new CompressedPayload(originalId, mode, data, originalSize);
   });

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
