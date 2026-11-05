package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：提交一个兑换码。 */
public record GiftSubmitPayload(String code) implements CustomPacketPayload {

    public static final Type<GiftSubmitPayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_submit"));

    /** 客户端可能提交任意长度的字符串，截一下避免大包 */
    private static final int MAX_LEN = 64;

    public static final StreamCodec<FriendlyByteBuf, GiftSubmitPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public GiftSubmitPayload decode(FriendlyByteBuf buf) {
                return new GiftSubmitPayload(buf.readUtf(MAX_LEN));
            }

            @Override
            public void encode(FriendlyByteBuf buf, GiftSubmitPayload payload) {
                String code = payload.code() == null ? "" : payload.code();
                if (code.length() > MAX_LEN) {
                    code = code.substring(0, MAX_LEN);
                }
                buf.writeUtf(code, MAX_LEN);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
