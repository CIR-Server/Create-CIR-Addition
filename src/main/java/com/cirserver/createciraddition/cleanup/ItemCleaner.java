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

    /** 原版全部 17 种潜影盒。逐个列出，不用后缀通配。 */
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> SHULKER_BOXES =
        java.util.Set.of(
            net.minecraft.resources.ResourceLocation.parse("minecraft:shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:white_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:orange_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:magenta_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:light_blue_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:yellow_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:lime_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:pink_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:gray_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:light_gray_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:cyan_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:purple_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:blue_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:brown_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:green_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:red_shulker_box"),
            net.minecraft.resources.ResourceLocation.parse("minecraft:black_shulker_box")
        );
    /**
     * //为避免垃圾桶bug，下列物品不清扫
     *
     * <p>判定顺序：
     * <ol>
     *   <li>可堆叠的：只排除旗帜，其余一律清理</li>
     *   <li>不可堆叠的：潜影盒 / Create 工具箱 → 跳过</li>
     *   <li>再查附魔 → 跳过</li>
     *   <li>都不符合 → 清理</li>
     * </ol>
     *
     * <p>注意：has() 对默认值不为 null 的组件恒为 true（ENCHANTMENTS 这类默认是空实例），
     * 所以下面都按「内容是否为空」判断，不能用 has()。
     */
    private static String skipReason(ItemStack stack) {
        // 旗帜：不分堆叠与否，一律跳过
        if (stack.getItem() instanceof net.minecraft.world.item.BannerItem) {
            return "banner";
        }
        if (!stack.getOrDefault(
                net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY
            ).layers().isEmpty()) {
            return "banner_patterns";
        }

        // 可堆叠的普通物品：直接清理
        if (stack.getMaxStackSize() > 1) {
            return null;
        }

        // 以下只针对不可堆叠的物品，按物品 ID 判断（箱子里有没有东西都不影响 ID）
        net.minecraft.resources.ResourceLocation itemId =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());

        // 潜影盒：逐个列 ID，不用后缀通配，避免误伤或漏判
        if (SHULKER_BOXES.contains(itemId)) {
            return "shulker_box";
        }
        // 带附魔的：数据包重载后旧附魔引用会失效，客户端编码时找不到 id
        if (!stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY
            ).isEmpty()) {
            return "enchanted";
        }
        return null;
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
                    String reason = skipReason(stack);
                    if (reason != null) {
                        skipped++;
                        if (skipped <= 5) {
                            CreateCIRAddition.LOGGER.info(
                                "Create CIR Addition: skip {} ({})", stack.getItem(), reason
                            );
                        }
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

    /**
     * 手动清理后的全服广播：无论清到几个都带上「打开垃圾桶」按钮。
     *
     * <p>桶里有东西时按钮可点；桶空时按钮会提示已过期，同样能点。
     */
    public static void broadcastManual(MinecraftServer server, int count) {
        try {
            MutableComponent msg;
            if (count > 0) {
                msg = Component.translatable("cir.cleanup.done", count).withStyle(ChatFormatting.GREEN);
            } else {
                msg = Component.translatable("cir.cleanup.none").withStyle(ChatFormatting.GREEN);
            }
            msg.append(" ").append(trashBinButton());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(msg);
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: failed to broadcast cleanup", t);
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
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cir trashBin"))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("cir.cleanup.openbin.hover", TrashBin.DEFAULT_DURATION_SECONDS / 60)
                    ))
                    .withColor(ChatFormatting.GREEN)
            );
    }
}
