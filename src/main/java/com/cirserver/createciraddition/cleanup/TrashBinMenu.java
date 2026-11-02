package com.cirserver.createciraddition.cleanup;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 垃圾桶界面（6 行 × 9 列）。
 *
 * <p>布局和老版一致：
 * <ul>
 *   <li>0-44：物品展示区（点一下取走）</li>
 *   <li>45：上一页　49：页码　53：下一页</li>
 *   <li>46-48、50-52：灰色玻璃板占位</li>
 * </ul>
 *
 * <p>玩家的背包槽位全部是 {@link LockedSlot}（不能放、不能拿），
 * 所以玩家只能往背包里收东西，没法把自己身上的物品塞进垃圾桶。
 */
public class TrashBinMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int TOTAL_SLOTS = 54;
    private static final int ITEMS_PER_PAGE = 45;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final SimpleContainer container;
    private final Player player;
    private int currentPage = 0;

    public TrashBinMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, playerInventory.player);
    }

    public TrashBinMenu(int containerId, Inventory playerInventory, Player player) {
        super(ModMenuTypes.TRASH_BIN_MENU.get(), containerId);
        this.player = player;
        this.container = new SimpleContainer(TOTAL_SLOTS);

        // 上半：垃圾桶展示区
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                this.addSlot(new LockedSlot(this.container, index, 8 + col * 18, 18 + row * 18));
            }
        }

        // 下半：玩家背包三行
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new LockedSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new LockedSlot(playerInventory, col, 8 + col * 18, 198));
        }

        this.refreshPage();
    }

    private void refreshPage() {
        TrashBin bin = TrashBin.getInstance();

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            this.container.setItem(i, ItemStack.EMPTY);
        }

        if (bin != null && !bin.isExpired()) {
            List<ItemStack> pageItems = bin.getPage(this.currentPage);
            for (int i = 0; i < pageItems.size(); i++) {
                this.container.setItem(i, pageItems.get(i).copy());
            }
        }

        this.setupControlRow(bin);
    }

    private void setupControlRow(TrashBin bin) {
        for (int i = ITEMS_PER_PAGE; i < TOTAL_SLOTS; i++) {
            this.container.setItem(i, ItemStack.EMPTY);
        }

        int totalPages = bin != null ? bin.getTotalPages() : 1;

        if (this.currentPage > 0) {
            ItemStack prevArrow = new ItemStack(Items.ARROW);
            prevArrow.set(DataComponents.CUSTOM_NAME, Component.literal("§e上一页"));
            this.container.setItem(SLOT_PREV, prevArrow);
        }

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        pageInfo.set(
            DataComponents.CUSTOM_NAME,
            Component.literal("§7第 " + (this.currentPage + 1) + "/" + totalPages + " 页")
        );
        this.container.setItem(SLOT_PAGE_INFO, pageInfo);

        if (this.currentPage < totalPages - 1) {
            ItemStack nextArrow = new ItemStack(Items.ARROW);
            nextArrow.set(DataComponents.CUSTOM_NAME, Component.literal("§e下一页"));
            this.container.setItem(SLOT_NEXT, nextArrow);
        }

        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = ITEMS_PER_PAGE; i < TOTAL_SLOTS; i++) {
            if (i != SLOT_PREV && i != SLOT_PAGE_INFO && i != SLOT_NEXT) {
                this.container.setItem(i, glass.copy());
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // 只处理垃圾桶自己的 54 个槽位，玩家背包区什么都不做
        if (slotId < 0 || slotId >= TOTAL_SLOTS) {
            return;
        }

        TrashBin bin = TrashBin.getInstance();
        if (bin == null || bin.isExpired()) {
            player.closeContainer();
            return;
        }

        if (slotId == SLOT_PREV && this.currentPage > 0) {
            this.currentPage--;
            this.refreshPage();
            return;
        }

        if (slotId == SLOT_NEXT && this.currentPage < bin.getTotalPages() - 1) {
            this.currentPage++;
            this.refreshPage();
            return;
        }

        if (slotId < ITEMS_PER_PAGE) {
            int globalIndex = this.currentPage * ITEMS_PER_PAGE + slotId;
            if (player instanceof ServerPlayer serverPlayer && bin.claimItem(globalIndex, serverPlayer)) {
                this.container.setItem(slotId, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 禁止 shift 快捷移动
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        TrashBin bin = TrashBin.getInstance();
        return bin != null && !bin.isExpired();
    }

    /** 只读槽位：不能放入，也不能直接拿走（要靠 clicked 里的逻辑取物品） */
    private static class LockedSlot extends Slot {
        public LockedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
