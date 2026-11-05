package com.cirserver.createciraddition.client;

import com.cirserver.createciraddition.gift.GiftCodeMenu;
import com.cirserver.createciraddition.network.GiftResultPayload;
import com.cirserver.createciraddition.network.GiftSubmitPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 兑换码界面。两个状态：输入、结果。
 */
public class GiftCodeScreen extends AbstractContainerScreen<GiftCodeMenu> {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 130;

    private static final int COLOR_OK = 0xC8FF3C;
    private static final int COLOR_RED = 0xFF5555;

    private enum Stage {
        INPUT,
        RESULT
    }

    private Stage stage = Stage.INPUT;
    private GiftResultPayload.Result result = GiftResultPayload.Result.INVALID;

    private EditBox input;
    private Component status = Component.empty();
    private int statusColor = 0xFFFFFF;

    public GiftCodeScreen(GiftCodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos;
        int top = this.topPos;

        if (this.stage == Stage.INPUT) {
            this.input = new EditBox(
                this.font, left + 20, top + 46, PANEL_WIDTH - 40, 20,
                Component.translatable("cir.gift.hint")
            );
            this.input.setMaxLength(64);
            this.input.setHint(Component.translatable("cir.gift.hint"));
            this.addRenderableWidget(this.input);
            this.setInitialFocus(this.input);

            this.addRenderableWidget(Button.builder(
                Component.translatable("cir.gift.button"),
                b -> this.submit()
            ).bounds(left + 20, top + 76, PANEL_WIDTH - 40, 20).build());
            return;
        }

        // 结果页：确认 + 返回兑换
        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.confirm"),
            b -> this.onClose()
        ).bounds(left + 20, top + 76, 84, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.back"),
            b -> this.backToInput()
        ).bounds(left + 116, top + 76, 84, 20).build());
    }

    private void submit() {
        String code = this.input.getValue().trim();
        if (code.isEmpty()) {
            this.setStatus(Component.translatable("cir.gift.empty"), COLOR_RED);
            return;
        }
        PacketDistributor.sendToServer(new GiftSubmitPayload(code));
    }

    /** 服务端把结果发回来时调用 */
    public void onResult(GiftResultPayload.Result result) {
        this.result = result;
        this.stage = Stage.RESULT;
        this.status = Component.empty();
        this.rebuildWidgets();
    }

    private void backToInput() {
        this.stage = Stage.INPUT;
        this.status = Component.empty();
        this.rebuildWidgets();
    }

    private void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    /** 结果页中央显示的文字 */
    private Component resultText() {
        return switch (this.result) {
            case OK -> Component.translatable("cir.gift.ok");

            case ALREADY -> Component.translatable("cir.gift.already");
            case BROKEN -> Component.translatable("cir.gift.expired");
            case INVALID -> Component.translatable("cir.gift.invalid");
        };
    }

    private int resultColor() {
        return this.result == GiftResultPayload.Result.OK ? COLOR_OK : COLOR_RED;
    }

    /**
     * 输入框有焦点时把按键全交给它。
     *
     * AbstractContainerScreen 默认把背包键（E）当成关闭界面，不拦的话打字母 e 会直接关窗。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        if (this.input != null && this.input.isFocused()) {
            if (this.input.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 输入框在时，字符输入也走它 */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.input != null && this.input.isFocused()) {
            return this.input.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xCC000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF808080);
        graphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF808080);
        graphics.fill(left, top, left + 1, top + PANEL_HEIGHT, 0xFF808080);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF808080);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int top = this.topPos;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 14, 0xFFFFFF);

        if (this.stage == Stage.INPUT) {
            graphics.drawCenteredString(
                this.font, Component.translatable("cir.gift.prompt"), this.width / 2, top + 30, 0xA0A0A0
            );
            if (!this.status.getString().isEmpty()) {
                graphics.drawCenteredString(
                    this.font, this.status, this.width / 2, top + PANEL_HEIGHT - 14, this.statusColor
                );
            }
            return;
        }

        // 结果页：中央加粗文本
        Component text = this.resultText().copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        int half = this.font.width(text) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0f - half, top + 46.0f, 0);
        graphics.pose().scale(1.15f, 1.15f, 1.0f);
        graphics.drawString(this.font, text, 0, 0, this.resultColor(), false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题自己画，背包名不显示
    }
}
