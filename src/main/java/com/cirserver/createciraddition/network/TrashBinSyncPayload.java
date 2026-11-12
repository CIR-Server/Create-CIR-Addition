package com.cirserver.createciraddition.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：把垃圾桶里的东西同步下去。
 *
 * <p>为什么需要这个：{@code TrashBin} 是纯静态单例，只有服务端清理物品时才会赋值。
 * 单人游戏里集成服务端和客户端共用一个 JVM，静态变量是同一份，所以客户端能读到。
 * 专用服务器上是两个进程，客户端那份永远是 null，于是：
 * <ul>
 *   <li>{@code TrashBinMenu.clicked} 里 bin == null 会直接 closeContainer，点一下就关窗；</li>
 *   <li>{@code stillValid} 返回 false，服务端也会主动关窗；</li>
 *   <li>刷新页面时容器全空，客户端根本看不到桶里有什么。</li>
 * </ul>
 *
 * <p>这个包在服务端打开菜单时发一次，客户端收到后存进自己的副本，
 * 之后 GUI 就有真实数据可以渲染和点击了。
 */
public record TrashBinSyncPayload(List<ItemStack> items, int remainingSeconds) implements CustomPacketPayload {

    public static final Type<TrashBinSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("create_cir_addition", "trash_bin_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrashBinSyncPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public TrashBinSyncPayload decode(RegistryFriendlyByteBuf buf) {
                int n = buf.readVarInt();
                List<ItemStack> items = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                int remaining = buf.readVarInt();
                return new TrashBinSyncPayload(items, remaining);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, TrashBinSyncPayload payload) {
                buf.writeVarInt(payload.items().size());
                for (ItemStack stack : payload.items()) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                }
                buf.writeVarInt(Math.max(0, payload.remainingSeconds()));
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
