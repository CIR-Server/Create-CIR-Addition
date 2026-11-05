package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：看完免责声明，请求打开目标界面。 */
public record GiftDisclaimerAckPayload(boolean manage) implements CustomPacketPayload {

    public static final Type<GiftDisclaimerAckPayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_disclaimer_ack"));

    public static final StreamCodec<FriendlyByteBuf, GiftDisclaimerAckPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.manage()),
            buf -> new GiftDisclaimerAckPayload(buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
