package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：新建兑换码。字段都是界面里原样输入的文本。 */
public record GiftCreatePayload(
    String code,
    String items,
    String effects,
    String xp
) implements CustomPacketPayload {

    public static final Type<GiftCreatePayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_create"));

    private static final int MAX = 512;

    public static final StreamCodec<FriendlyByteBuf, GiftCreatePayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public GiftCreatePayload decode(FriendlyByteBuf buf) {
                return new GiftCreatePayload(buf.readUtf(64), buf.readUtf(MAX), buf.readUtf(MAX), buf.readUtf(16));
            }

            @Override
            public void encode(FriendlyByteBuf buf, GiftCreatePayload payload) {
                buf.writeUtf(clip(payload.code(), 64), 64);
                buf.writeUtf(clip(payload.items(), MAX), MAX);
                buf.writeUtf(clip(payload.effects(), MAX), MAX);
                buf.writeUtf(clip(payload.xp(), 16), 16);
            }

            private String clip(String s, int max) {
                if (s == null) {
                    return "";
                }
                return s.length() > max ? s.substring(0, max) : s;
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
