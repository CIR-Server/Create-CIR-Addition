package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：对某个兑换码执行操作。 */
public record GiftManageActionPayload(Action action, String code) implements CustomPacketPayload {

    public static final Type<GiftManageActionPayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_manage_action"));

    public static final StreamCodec<FriendlyByteBuf, GiftManageActionPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.action().ordinal());
                buf.writeUtf(payload.code() == null ? "" : payload.code(), 64);
            },
            buf -> {
                int i = buf.readByte();
                Action[] all = Action.values();
                return new GiftManageActionPayload(i >= 0 && i < all.length ? all[i] : Action.REFRESH, buf.readUtf(64));
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        /** 作废（保留条目，标记 disabled） */
        DISABLE,
        /** 删除条目和兑换记录 */
        DELETE,
        /** 只刷新列表 */
        REFRESH
    }
}
