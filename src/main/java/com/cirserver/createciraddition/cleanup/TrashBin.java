package com.cirserver.createciraddition.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * 被清理掉的掉落物暂存区。
 *
 * <p>从老版 Duolingo Super 移植。清理时收集到的物品不会凭空消失，
 * 而是暂存在这里，玩家可以通过 {@code /cir trashBin} 打开界面取回。
 *
 * <h2>为什么分成两份实例</h2>
 *
 * <p>服务端和客户端是两个进程，静态变量各有一份。
 * 服务端那份在 {@code ItemCleaner.cleanup} 里赋值，是真正的数据源；
 * 客户端那份只能靠 {@link com.cirserver.createciraddition.network.TrashBinSyncPayload}
 * 在打开界面时同步过来。
 *
 * <p>如果不区分，客户端永远拿到 null，界面会表现为「点一下就关」和「里面空空如也」
 * —— 这正是专用服务器上的那个 bug。
 */
public class TrashBin {

    /** 每页放多少个物品（界面 6 行，最后一行留给翻页按钮） */
    private static final int ITEMS_PER_PAGE = 45;

    /** 垃圾桶有效期：1 小时 */
    public static final int DEFAULT_DURATION_SECONDS = 3600;

    /** 服务端真实数据（只有 ItemCleaner 会写） */
    private static TrashBin instance = null;

    /** 客户端副本（只有收到同步包时才会写） */
    private static TrashBin clientInstance = null;

    private final List<ItemStack> items;
    private final long createdAt;
    private final int durationSeconds;

    public TrashBin(List<ItemStack> items, int durationSeconds) {
        this.items = new ArrayList<>(items);
        this.createdAt = System.currentTimeMillis();
        this.durationSeconds = durationSeconds;
    }

    /**
     * 取当前端对应的实例。
     *
     * <p>不能靠 {@code FMLEnvironment.dist} 区分：单人游戏的 dist 也是 CLIENT，
     * 那样会把集成服务端写好的真实数据挡掉。
     *
     * <p>正确的判断是谁有数据：服务端（含单人）一定写过 {@code instance}，
     * 专用服务器的客户端只能靠同步包拿到 {@code clientInstance}。
     */
    public static TrashBin getInstance() {
        if (instance != null) {
            if (instance.isExpired()) {
                instance = null;
            } else {
                return instance;
            }
        }
        if (clientInstance != null && clientInstance.isExpired()) {
            clientInstance = null;
        }
        return clientInstance;
    }

    /** 服务端清理时写入真实数据 */
    public static void setInstance(TrashBin bin) {
        instance = bin;
    }

    /** 客户端收到同步包时写入副本 */
    public static void setClientInstance(TrashBin bin) {
        clientInstance = bin;
    }

    /**
     * 从服务端同步包构造客户端副本。
     *
     * <p>用「剩余秒数」而不是原始创建时间：客户端和服务端的
     * {@code System.currentTimeMillis()} 基准可能不同，直接用原始时间会立刻过期。
     */
    public static TrashBin fromSync(List<ItemStack> items, int remainingSeconds) {
        int duration = Math.max(1, remainingSeconds);
        return new TrashBin(items, duration);
    }

    public static void clear() {
        instance = null;
        clientInstance = null;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.createdAt > (long) this.durationSeconds * 1000L;
    }

    public int getTotalItems() {
        return this.items.size();
    }

    /** 取指定下标的原始物品（供同步包遍历用） */
    public ItemStack getItemAt(int globalIndex) {
        if (globalIndex < 0 || globalIndex >= this.items.size()) {
            return ItemStack.EMPTY;
        }
        return this.items.get(globalIndex);
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

    /** 从垃圾桶取走一件物品。 */
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
