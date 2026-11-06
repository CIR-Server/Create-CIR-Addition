package com.cirserver.createciraddition.client;

import com.cirserver.createciraddition.gift.GiftManageMenu;
import com.cirserver.createciraddition.network.GiftCreatePayload;
import com.cirserver.createciraddition.network.GiftManageActionPayload;
import com.cirserver.createciraddition.network.GiftManageDataPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 兑换码管理界面。两个状态：列表、新建。
 */
public class GiftManageScreen extends AbstractContainerScreen<GiftManageMenu> {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_ROWS = 6;

    private enum Stage {
        LIST,
        CREATE
    }

    private Stage stage = Stage.LIST;
    private List<GiftManageDataPayload.Row> rows = new ArrayList<>();
    private Component status = Component.empty();
    private int statusColor = 0xFFFFFF;

    // 新建页的输入框
    private EditBox codeInput;
    private EditBox itemsInput;
    private EditBox effectsInput;
    private EditBox xpInput;

    public GiftManageScreen(GiftManageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    /** 服务端推列表过来 */
    public void onData(GiftManageDataPayload payload) {
        this.rows = payload.rows();
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        if (this.stage == Stage.LIST) {
            this.buildList();
        } else {
            this.buildCreate();
        }
    }

    // ------------------------------------------------------------------
    // 列表页
    // ------------------------------------------------------------------

    private void buildList() {
        int left = this.leftPos;
        int top = this.topPos;

        int shown = Math.min(this.rows.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            GiftManageDataPayload.Row row = this.rows.get(i);
            int y = top + 28 + i * ROW_HEIGHT;

            String code = row.code() + (row.disabled() ? " (已作废)" : "");
            if (code.length() > 22) {
                code = code.substring(0, 22);
            }
            String finalCode = code;
            int finalI = i;

            this.addRenderableWidget(Button.builder(
                Component.translatable("cir.gift.manage.disable"),
                b -> this.disable(this.rows.get(finalI).code())
            ).bounds(left + 210, y, 50, 18).build());

            this.addRenderableWidget(Button.builder(
                Component.translatable("cir.gift.manage.delete"),
                b -> this.delete(this.rows.get(finalI).code())
            ).bounds(left + 266, y, 50, 18).build());
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.manage.create"),
            b -> {
                this.stage = Stage.CREATE;
                this.status = Component.empty();
                this.rebuildWidgets();
            }
        ).bounds(left + 20, top + PANEL_HEIGHT - 30, 100, 20).build());
    }

    private void disable(String code) {
        PacketDistributor.sendToServer(new GiftManageActionPayload(GiftManageActionPayload.Action.DISABLE, code));
    }

    private void delete(String code) {
        PacketDistributor.sendToServer(new GiftManageActionPayload(GiftManageActionPayload.Action.DELETE, code));
    }

    // ------------------------------------------------------------------
    // 新建页
    // ------------------------------------------------------------------

    private void buildCreate() {
        int left = this.leftPos;
        int top = this.topPos;

        int fieldX = left + 96;
        int fieldW = PANEL_WIDTH - 116;

        this.codeInput = new EditBox(this.font, fieldX, top + 26, fieldW, 16, Component.empty());
        this.codeInput.setMaxLength(64);
        this.addRenderableWidget(this.codeInput);

        this.itemsInput = new EditBox(this.font, left + 20, top + 76, PANEL_WIDTH - 40, 16, Component.empty());
        this.itemsInput.setMaxLength(512);
        this.addRenderableWidget(this.itemsInput);

        this.effectsInput = new EditBox(this.font, left + 20, top + 126, PANEL_WIDTH - 40, 16, Component.empty());
        this.effectsInput.setMaxLength(512);
        this.addRenderableWidget(this.effectsInput);

        this.xpInput = new EditBox(this.font, fieldX, top + 150, 60, 16, Component.empty());
        this.xpInput.setMaxLength(10);
        this.addRenderableWidget(this.xpInput);

        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.manage.validate"),
            b -> this.validateAndSave()
        ).bounds(left + 20, top + PANEL_HEIGHT - 30, 110, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.manage.cancel"),
            b -> {
                this.stage = Stage.LIST;
                this.status = Component.empty();
                this.rebuildWidgets();
            }
        ).bounds(left + 140, top + PANEL_HEIGHT - 30, 80, 20).build());
    }

    /** 本地先校验一遍 id，有错就清空对应输入框 */
    private void validateAndSave() {
        List<String> badItems = new ArrayList<>();
        List<String> badEffects = new ArrayList<>();

        String itemsRaw = this.itemsInput.getValue().trim();
        if (!itemsRaw.isEmpty()) {
            for (String seg : itemsRaw.split(";")) {
                String s = seg.trim();
                if (s.isEmpty()) {
                    continue;
                }
                String id = s.split(",")[0].trim();
                if (ResourceLocation.tryParse(id) == null || !BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(id))) {
                    badItems.add(id);
                }
            }
        }

        String effectsRaw = this.effectsInput.getValue().trim();
        if (!effectsRaw.isEmpty()) {
            for (String seg : effectsRaw.split(";")) {
                String s = seg.trim();
                if (s.isEmpty()) {
                    continue;
                }
                String id = s.split(",")[0].trim();
                if (ResourceLocation.tryParse(id) == null
                    || BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.tryParse(id)).isEmpty()) {
                    badEffects.add(id);
                }
            }
        }

        if (!badItems.isEmpty()) {
            this.itemsInput.setValue("");
        }
        if (!badEffects.isEmpty()) {
            this.effectsInput.setValue("");
        }
        if (!badItems.isEmpty() || !badEffects.isEmpty()) {
            this.setStatus(Component.translatable("cir.gift.manage.badid"), 0xFF5555);
            return;
        }

        if (this.codeInput.getValue().trim().isEmpty()) {
            this.setStatus(Component.translatable("cir.gift.manage.nocode"), 0xFF5555);
            return;
        }

        PacketDistributor.sendToServer(new GiftCreatePayload(
            this.codeInput.getValue().trim(),
            itemsRaw,
            effectsRaw,
            this.xpInput.getValue().trim()
        ));
        this.setStatus(Component.translatable("cir.gift.manage.saved"), 0x55FF55);
    }

    /** 服务端返回新建结果 */
    public void onCreateResult(boolean ok, List<String> badIds) {
        if (ok) {
            this.setStatus(Component.translatable("cir.gift.manage.saved"), 0x55FF55);
            return;
        }
        if (!badIds.isEmpty()) {
            this.setStatus(Component.translatable("cir.gift.manage.badid"), 0xFF5555);
        } else {
            this.setStatus(Component.translatable("cir.gift.manage.savefail"), 0xFF5555);
        }
    }

    private void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

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

        int left = this.leftPos;
        int top = this.topPos;

        if (this.stage == Stage.LIST) {
            graphics.drawCenteredString(
                this.font, Component.translatable("cir.gift.manage.title"), this.width / 2, top + 8, 0xFFFFFF
            );

            if (this.rows.isEmpty()) {
                graphics.drawString(
                    this.font, Component.translatable("cir.gift.manage.empty"), left + 20, top + 30, 0xA0A0A0
                );
            } else {
                int shown = Math.min(this.rows.size(), MAX_ROWS);
                for (int i = 0; i < shown; i++) {
                    GiftManageDataPayload.Row row = this.rows.get(i);
                    int y = top + 28 + i * ROW_HEIGHT + 5;
                    String text = row.code() + (row.disabled() ? " (已作废)" : "");
                    graphics.drawString(this.font, text, left + 20, y, row.disabled() ? 0x888888 : 0xFFFFFF);
                }
                if (this.rows.size() > MAX_ROWS) {
                    graphics.drawString(
                        this.font,
                        Component.translatable("cir.gift.manage.more", this.rows.size() - MAX_ROWS),
                        left + 20, top + 28 + MAX_ROWS * ROW_HEIGHT, 0xA0A0A0
                    );
                }
            }
        } else {
            graphics.drawCenteredString(
                this.font, Component.translatable("cir.gift.manage.newtitle"), this.width / 2, top + 8, 0xFFFFFF
            );

            graphics.drawString(this.font, Component.translatable("cir.gift.manage.codelabel"), left + 20, top + 30, 0xA0A0A0);

            graphics.drawString(
                this.font,
                Component.translatable("cir.gift.manage.itemsline"),
                left + 20, top + 50, 0xA0A0A0
            );
            graphics.drawString(
                this.font,
                Component.translatable("cir.gift.manage.itemsexample"),
                left + 28, top + 62, 0xB8B8B8
            );

            graphics.drawString(
                this.font,
                Component.translatable("cir.gift.manage.effectsline"),
                left + 20, top + 100, 0xA0A0A0
            );
            graphics.drawString(
                this.font,
                Component.translatable("cir.gift.manage.effectsexample"),
                left + 28, top + 112, 0xB8B8B8
            );

            graphics.drawString(this.font, Component.translatable("cir.gift.manage.xplabel"), left + 20, top + 154, 0xA0A0A0);
        }

        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(
                this.font, this.status, this.width / 2, top + PANEL_HEIGHT - 44, this.statusColor
            );
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        for (var widget : this.children()) {
            if (widget instanceof EditBox box && box.isFocused()) {
                box.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (var widget : this.children()) {
            if (widget instanceof EditBox box && box.isFocused()) {
                return box.charTyped(codePoint, modifiers);
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
