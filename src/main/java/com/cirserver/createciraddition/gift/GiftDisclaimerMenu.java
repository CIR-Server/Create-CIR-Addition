package com.cirserver.createciraddition.gift;

import com.cirserver.createciraddition.cleanup.ModMenuTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 免责声明界面。玩家看完点「我已了解」后，再打开目标界面。
 *
 * <p>目标只分两种：兑换界面、管理界面。
 */
public class GiftDisclaimerMenu extends AbstractContainerMenu {

    /** 玩家看完免责声明后要打开的界面 */
    public enum Next {
        REDEEM,
        MANAGE
    }

    private final Next next;

    public GiftDisclaimerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, Next.REDEEM);
    }

    public GiftDisclaimerMenu(int containerId, Inventory playerInventory, Next next) {
        super(ModMenuTypes.GIFT_DISCLAIMER_MENU.get(), containerId);
        this.next = next;
    }

    public Next next() {
        return this.next;
    }

    public static void open(ServerPlayer player, Next next) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new GiftDisclaimerMenu(id, inv, next),
            Component.translatable("cir.gift.disclaimer.title")
        ));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
