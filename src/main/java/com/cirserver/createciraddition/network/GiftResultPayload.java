package com.cirserver.createciraddition.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：兑换结果。 */
public record GiftResultPayload(Result result) implements CustomPacketPayload {

    public static final Type<GiftResultPayload> TYPE =
        new Type(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "gift_result"));

    public static final StreamCodec<FriendlyByteBuf, GiftResultPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.result().ordinal()),
            buf -> {
                int i = buf.readByte();
                Result[] all = Result.values();
                return new GiftResultPayload(i >= 0 && i < all.length ? all[i] : Result.INVALID);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Result {
        /** 兑换成功 */
        OK,
        /** 码不存在 */
        INVALID,
        /** 这个码已经领过，或已被作废 */
        ALREADY,
        /** 码存在但奖品配置无效 */
        BROKEN
    }
}
