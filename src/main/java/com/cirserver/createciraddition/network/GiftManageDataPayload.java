package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：兑换码列表。 */
public record GiftManageDataPayload(List<Row> rows) implements CustomPacketPayload {

    public static final Type<GiftManageDataPayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_manage_data"));

    public static final StreamCodec<FriendlyByteBuf, GiftManageDataPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public GiftManageDataPayload decode(FriendlyByteBuf buf) {
                int n = buf.readVarInt();
                List<Row> rows = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    rows.add(new Row(buf.readUtf(64), buf.readBoolean()));
                }
                return new GiftManageDataPayload(rows);
            }

            @Override
            public void encode(FriendlyByteBuf buf, GiftManageDataPayload payload) {
                buf.writeVarInt(payload.rows().size());
                for (Row row : payload.rows()) {
                    buf.writeUtf(row.code(), 64);
                    buf.writeBoolean(row.disabled());
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Row(String code, boolean disabled) {
    }
}
