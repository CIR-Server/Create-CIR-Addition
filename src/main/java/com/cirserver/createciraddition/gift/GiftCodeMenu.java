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
 * 兑换码界面用的空菜单。
 *
 * <p>没有槽位，纯粹是为了走原版 {@code openMenu} → {@code RegisterMenuScreens} 这条开屏链路，
 * 和垃圾桶界面保持一致。输入框、按钮都在 Screen 里画。
 */
public class GiftCodeMenu extends AbstractContainerMenu {

    public GiftCodeMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.GIFT_CODE_MENU.get(), containerId);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new GiftCodeMenu(id, inv),
            Component.translatable("cir.gift.title")
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
