package com.cirserver.createciraddition.client;

import com.cirserver.createciraddition.cleanup.TrashBinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 垃圾桶界面。
 *
 * <p>不做自定义绘制，直接借用原版 6 行箱子的贴图（{@code generic_54}）。
 * 布局是 6 行 × 9 列，和 {@link TrashBinMenu} 的槽位数量完全对得上，
 * 所以贴图不用裁也不用缩放。
 *
 * <p>为什么必须有这个类：NeoForge 的自定义 {@code MenuType} 如果在客户端
 * 没注册 Screen，{@code ServerPlayer#openMenu} 发出的 OpenScreen 包到达客户端后
 * 找不到对应的界面工厂，结果是**什么都不显示**（静默失败，服务端还以为打开了）。
 * 早期版本就是因为漏了这个类，导致 {@code /cir trashbin} 点了没反应。
 */
public class TrashBinScreen extends AbstractContainerScreen<TrashBinMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public TrashBinScreen(TrashBinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // generic_54.png 是 176×222，正好 6 行
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
