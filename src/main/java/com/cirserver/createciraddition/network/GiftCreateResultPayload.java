package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：新建兑换码的校验结果。 */
public record GiftCreateResultPayload(boolean ok, List<String> badIds) implements CustomPacketPayload {

    public static final Type<GiftCreateResultPayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_create_result"));

    public static final StreamCodec<FriendlyByteBuf, GiftCreateResultPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public GiftCreateResultPayload decode(FriendlyByteBuf buf) {
                boolean ok = buf.readBoolean();
                int n = buf.readVarInt();
                List<String> bad = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    bad.add(buf.readUtf(128));
                }
                return new GiftCreateResultPayload(ok, bad);
            }

            @Override
            public void encode(FriendlyByteBuf buf, GiftCreateResultPayload payload) {
                buf.writeBoolean(payload.ok());
                buf.writeVarInt(payload.badIds().size());
                for (String id : payload.badIds()) {
                    buf.writeUtf(id, 128);
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
