package com.cirserver.createciraddition.gift;

import com.cirserver.createciraddition.cleanup.ModMenuTypes;
import com.cirserver.createciraddition.network.GiftServerHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 兑换码管理界面的菜单。没有槽位。 */
public class GiftManageMenu extends AbstractContainerMenu {

    public GiftManageMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.GIFT_MANAGE_MENU.get(), containerId);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new GiftManageMenu(id, inv),
            Component.translatable("cir.gift.manage.title")
        ));
        // 界面构建完成后把列表推过去
        GiftServerHandler.sendManageData(player);
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
