package com.cirserver.createciraddition.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 被清理掉的掉落物暂存区。
 *
 * <p>从老版 Duolingo Super 移植。清理时收集到的物品不会凭空消失，
 * 而是进到这里，玩家可以在有效期内用 {@code /cir trashbin} 打开界面取回。
 *
 * <p>有效期固定 <b>1 小时</b>（{@link #DEFAULT_DURATION_SECONDS}）。过期后实例直接丢弃，
 * 界面也会自动关闭。
 *
 * <p>注意：这是<b>全局单例</b>——整个服务器共用一个垃圾桶，不是每人一份。
 * 和老版行为一致：谁先打开谁先拿。
 */
public class TrashBin {

    /** 每页放多少个物品（界面 6 行，最后一行留给翻页按钮） */
    private static final int ITEMS_PER_PAGE = 45;

    /** 垃圾桶有效期：1 小时 */
    public static final int DEFAULT_DURATION_SECONDS = 3600;

    private static TrashBin instance = null;

    private final List<ItemStack> items;
    private final long createdAt;
    private final int durationSeconds;

    public TrashBin(List<ItemStack> items, int durationSeconds) {
        this.items = new ArrayList<>(items);
        this.createdAt = System.currentTimeMillis();
        this.durationSeconds = durationSeconds;
    }

    public static TrashBin getInstance() {
        if (instance != null && instance.isExpired()) {
            instance = null;
        }
        return instance;
    }

    public static void setInstance(TrashBin bin) {
        instance = bin;
    }

    public static void clear() {
        instance = null;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.createdAt > (long) this.durationSeconds * 1000L;
    }

    public int getTotalItems() {
        return this.items.size();
    }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) this.items.size() / ITEMS_PER_PAGE));
    }

    public List<ItemStack> getPage(int page) {
        if (page >= 0 && page < this.getTotalPages()) {
            int start = page * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, this.items.size());
            return this.items.subList(start, end);
        }
        return Collections.emptyList();
    }

    /**
     * 从垃圾桶取走一件物品。
     *
     * @param globalIndex 全局索引（跨页）
     * @return 取走成功返回 true；背包满了或索引无效返回 false
     */
    public boolean claimItem(int globalIndex, ServerPlayer player) {
        if (globalIndex < 0 || globalIndex >= this.items.size()) {
            return false;
        }
        ItemStack stack = this.items.get(globalIndex);
        if (stack.isEmpty()) {
            return false;
        }
        if (player.getInventory().add(stack.copy())) {
            this.items.set(globalIndex, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    public int getRemainingSeconds() {
        long elapsed = (System.currentTimeMillis() - this.createdAt) / 1000L;
        return Math.max(0, this.durationSeconds - (int) elapsed);
    }
}
