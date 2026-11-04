package com.cirserver.createciraddition.cleanup;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 地面掉落物清理（扫地）。
 *
 * 从老版 Duolingo Super 的 {@code CleanupScheduler} 移植，改动：
 */
public final class ItemCleaner {

    private ItemCleaner() {
    }

    /**
 * 这个物品能不能安全地经网络发给客户端。
 *
 * 垃圾桶里的东西是要显示在界面上的。如果某个 ItemStack 的数据组件没法序列化，
 */
    private static boolean isNetworkSafe(ItemStack stack, net.minecraft.core.RegistryAccess registries) {
        net.minecraft.network.RegistryFriendlyByteBuf buf =
            new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            buf.release();
        }
    }

    /** 清理所有维度的地面掉落物，并把它们存进垃圾桶。 */
    public static int cleanup(MinecraftServer server, boolean ignoreNamed) {
        List<ItemStack> collected = new ArrayList<>();
        int removed = 0;

        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> toRemove = new ArrayList<>();
            try {
                for (Entity entity : level.getEntities().getAll()) {
                    if (!(entity instanceof ItemEntity itemEntity)) continue;
                    if (ignoreNamed && itemEntity.hasCustomName()) continue;
                    ItemStack stack = itemEntity.getItem();
                    if (stack.isEmpty()) continue;
                    // 无论能否进桶都得清掉，所以先登记，再决定要不要收进收集表
                    toRemove.add(itemEntity);
                    if (!isNetworkSafe(stack, server.registryAccess())) {
                        // 这种物品一旦进垃圾桶，客户端点它就会在 container_click 里编码失败并断连
                        CreateCIRAddition.LOGGER.warn(
                            "Create CIR Addition: destroyed an unencodable item {} x{} at {} (it would disconnect clients from the trash bin)",
                            stack.getItem(), stack.getCount(), itemEntity.blockPosition()
                        );
                        continue;
                    }
                    collected.add(stack.copy());
                }
            } catch (Throwable t) {
                CreateCIRAddition.LOGGER.debug(
                    "Create CIR Addition: failed to enumerate items in {}", level.dimension().location(), t
                );
                continue;
            }

            for (ItemEntity e : toRemove) {
                try {
                    e.discard();
                    removed++;
                } catch (Throwable t) {
                    CreateCIRAddition.LOGGER.debug("Create CIR Addition: failed to discard an item entity", t);
                }
            }
        }

        // 存进垃圾桶，有效期 1 小时
        if (!collected.isEmpty()) {
            TrashBin.setInstance(new TrashBin(collected, TrashBin.DEFAULT_DURATION_SECONDS));
            CreateCIRAddition.LOGGER.info(
                "Create CIR Addition: cleaned {} dropped item(s), stored in TrashBin ({} min)",
                removed, TrashBin.DEFAULT_DURATION_SECONDS / 60
            );
        }

        return removed;
    }

    /** 用配置里的默认设置跑一次清理 */
    public static int cleanup(MinecraftServer server) {
        boolean ignoreNamed;
        try {
            ignoreNamed = CirConfig.CLEANUP_IGNORE_NAMED.get();
        } catch (Throwable t) {
            ignoreNamed = true;
        }
        return cleanup(server, ignoreNamed);
    }

    /** 手动清理后给执行者回执 */
    public static void notifyManual(ServerPlayer player, int count) {
        try {
            if (count <= 0) {
                player.sendSystemMessage(
                    Component.translatable("cir.cleanup.none").withStyle(ChatFormatting.GREEN)
                );
                return;
            }
            MutableComponent msg = Component.translatable("cir.cleanup.done", count)
                .withStyle(ChatFormatting.GREEN);
            msg.append(" ").append(trashBinButton());
            player.sendSystemMessage(msg);
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: failed to send cleanup result", t);
        }
    }

    /** 自动清理（MSPT 超限触发）时的全服广播 */
    public static void broadcastAuto(MinecraftServer server, int count, String mspt) {
        try {
            MutableComponent msg = Component.translatable("cir.cleanup.auto", mspt, count)
                .withStyle(ChatFormatting.RED);
            msg.append(" ").append(trashBinButton());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(msg);
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: failed to broadcast cleanup", t);
        }
    }

    /** 可点击的「打开垃圾桶」按钮 */
    private static MutableComponent trashBinButton() {
        return Component.translatable("cir.cleanup.openbin")
            .withStyle(
                Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cir trashbin"))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("cir.cleanup.openbin.hover", TrashBin.DEFAULT_DURATION_SECONDS / 60)
                    ))
                    .withColor(ChatFormatting.GREEN)
            );
    }
}
