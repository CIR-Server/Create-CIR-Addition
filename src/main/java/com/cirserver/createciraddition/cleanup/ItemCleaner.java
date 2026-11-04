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
     * 把物品收拾成「客户端一定能编码」的样子；实在不行返回 null。
     *
     * <p>旗帜图案是静态注册表，客户端那头的 id 集合未必和服务端一致。灾厄旗帜带着
     * {@code minecraft:rhombus} 这类图案时，服务端编得出来、客户端点它却直接断连。
     * 这里把图案剥掉，保住物品本身——玩家拿回一面素色旗帜，总比整个丢了好。
     */
    private static ItemStack sanitize(ItemStack stack, net.minecraft.core.RegistryAccess registries) {
        ItemStack candidate = stack;
        if (stack.has(net.minecraft.core.component.DataComponents.BANNER_PATTERNS)) {
            candidate = stack.copy();
            candidate.remove(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
        }
        net.minecraft.network.RegistryFriendlyByteBuf buf =
            new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, candidate);
            return candidate;
        } catch (Throwable t) {
            return null;
        } finally {
            buf.release();
        }
    }

    /**
     * //为避免垃圾桶bug，下列物品不清扫
     */
    private static boolean shouldSkip(ItemStack stack) {
        // 所有旗帜：图案来自静态注册表，两端可能不一致
        if (stack.getItem() instanceof net.minecraft.world.item.BannerItem) {
            return true;
        }
        if (stack.has(net.minecraft.core.component.DataComponents.BANNER_PATTERNS)) {
            return true;
        }
        // 潜影盒、Create 工具箱这类：内部物品表随物品走
        if (stack.has(net.minecraft.core.component.DataComponents.CONTAINER)) {
            return true;
        }
        // 其它方块实体数据，比如带刷怪蛋内容的刷怪笼
        if (stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)) {
            return true;
        }
        // Create 工具箱：内部物品走自己的组件，不是标准 CONTAINER
        if (hasCreateToolboxInventory(stack)) {
            return true;
        }
        // 带附魔的：数据包重载后旧附魔引用会失效，客户端编码时找不到 id
        if (stack.has(net.minecraft.core.component.DataComponents.ENCHANTMENTS)) {
            return true;
        }
        return false;
    }

    /** Create 的 toolbox_inventory 组件。按 ID 查，避免对 Create 产生编译期依赖。 */
    private static final net.minecraft.resources.ResourceLocation TOOLBOX_INVENTORY =
        net.minecraft.resources.ResourceLocation.parse("create:toolbox_inventory");

    private static boolean hasCreateToolboxInventory(ItemStack stack) {
        net.minecraft.core.component.DataComponentType<?> type =
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(TOOLBOX_INVENTORY);
        return type != null && stack.has(type);
    }

    /** 清理所有维度的地面掉落物，并把它们存进垃圾桶。 */
    public static int cleanup(MinecraftServer server, boolean ignoreNamed) {
        List<ItemStack> collected = new ArrayList<>();
        int skipped = 0;
        int removed = 0;

        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> toRemove = new ArrayList<>();
            try {
                for (Entity entity : level.getEntities().getAll()) {
                    if (!(entity instanceof ItemEntity itemEntity)) continue;
                    if (ignoreNamed && itemEntity.hasCustomName()) continue;
                    ItemStack stack = itemEntity.getItem();
                    if (stack.isEmpty()) continue;
                    // 旗帜和带方块实体数据的物品完全不碰，留在原地
                    if (shouldSkip(stack)) {
                        skipped++;
                        continue;
                    }
                    // 之后能进桶的，先登记删除，再决定要不要收进收集表
                    toRemove.add(itemEntity);
                    ItemStack safe = sanitize(stack, server.registryAccess());
                    if (safe == null) {
                        // 这种物品一旦进垃圾桶，客户端点它就会在 container_click 里编码失败并断连
                        CreateCIRAddition.LOGGER.warn(
                            "Create CIR Addition: destroyed an unencodable item {} x{} at {} (it would disconnect clients from the trash bin)",
                            stack.getItem(), stack.getCount(), itemEntity.blockPosition()
                        );
                        continue;
                    }
                    collected.add(safe);
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
                "Create CIR Addition: cleaned {} dropped item(s) ({} skipped: banners / container data), stored {} in TrashBin ({} min)",
                removed, skipped, collected.size(), TrashBin.DEFAULT_DURATION_SECONDS / 60
            );
        } else if (skipped > 0) {
            CreateCIRAddition.LOGGER.info(
                "Create CIR Addition: cleaned {} dropped item(s), all {} skipped (banners / container data), TrashBin not created",
                removed, skipped
            );
        }

        return removed;
    }

    /**
     * 强制清扫：所有掉落物直接销毁，不跳过任何东西，也不进垃圾桶。
     *
     * <p>常规清扫会绕开旗帜、潜影盒、工具箱和带附魔的物品，遇到这些就清不干净。
     * 这条是兜底手段，宁可丢东西也要把地面清空。
     *
     * @return 销毁的掉落物实体数量
     */
    public static int forceCleanup(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> all = new ArrayList<>();
            try {
                for (Entity entity : level.getEntities().getAll()) {
                    if (entity instanceof ItemEntity itemEntity) {
                        all.add(itemEntity);
                    }
                }
            } catch (Throwable t) {
                CreateCIRAddition.LOGGER.debug(
                    "Create CIR Addition: failed to enumerate items in {}", level.dimension().location(), t
                );
                continue;
            }
            for (ItemEntity e : all) {
                try {
                    e.discard();
                    removed++;
                } catch (Throwable t) {
                    CreateCIRAddition.LOGGER.debug("Create CIR Addition: failed to discard an item entity", t);
                }
            }
        }
        CreateCIRAddition.LOGGER.info(
            "Create CIR Addition: FORCE cleanup removed {} dropped item(s), nothing kept", removed
        );
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
