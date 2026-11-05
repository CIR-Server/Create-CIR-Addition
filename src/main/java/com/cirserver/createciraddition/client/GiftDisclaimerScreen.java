package com.cirserver.createciraddition.client;

import com.cirserver.createciraddition.gift.GiftCodeMenu;
import com.cirserver.createciraddition.gift.GiftDisclaimerMenu;
import com.cirserver.createciraddition.gift.GiftManageMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** 免责声明界面。和兑换码那两个界面同尺寸。 */
public class GiftDisclaimerScreen extends AbstractContainerScreen<GiftDisclaimerMenu> {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;

    public GiftDisclaimerScreen(GiftDisclaimerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.disclaimer.accept"),
            b -> this.accept()
        ).bounds(this.leftPos + 20, this.topPos + PANEL_HEIGHT - 32, 130, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("cir.gift.disclaimer.cancel"),
            b -> this.onClose()
        ).bounds(this.leftPos + 170, this.topPos + PANEL_HEIGHT - 32, 130, 20).build());
    }

    private void accept() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            this.onClose();
            return;
        }
        // 客户端关界面，服务端打开目标界面
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.cirserver.createciraddition.network.GiftDisclaimerAckPayload(
                this.getMenu().next() == GiftDisclaimerMenu.Next.MANAGE
            )
        );
        this.onClose();
    }

    /**
     * 按字符宽度折行。
     *
     * <p>不用 font.split：它按空格分词，而中文整段没有空格，会被当成一个超长词，
     * 结果第一行只剩前面的英文，后面全部硬切到下一行。这里改成逐字符累加，
     * 碰到空格时记一个可断点，超宽且有断点就断在空格处，没有就硬切。
     */
    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int lastSpace = -1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                lastSpace = cur.length();
            }
            cur.append(c);
            if (this.font.width(cur.toString()) <= maxWidth) {
                continue;
            }

            // 只有当空格后面那段是纯英文单词时才回退到空格断行；
            // 中文没有词边界，回退会把它整段推到下一行，所以直接硬切。
            String tail = lastSpace > 0 ? cur.substring(lastSpace + 1) : "";
            boolean asciiWord = !tail.isEmpty() && tail.chars().allMatch(ch -> ch < 128);

            if (asciiWord && lastSpace > 0 && lastSpace < cur.length() - 1) {
                lines.add(cur.substring(0, lastSpace));
                String rest = cur.substring(lastSpace + 1);
                cur = new StringBuilder(rest);
                lastSpace = -1;
            } else {
                cur.deleteCharAt(cur.length() - 1);
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                }
                cur = new StringBuilder();
                cur.append(c);
                lastSpace = -1;
            }
        }

        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
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

        int left = this.leftPos;
        int top = this.topPos;

        graphics.drawCenteredString(
            this.font, Component.translatable("cir.gift.disclaimer.title"), this.width / 2, top + 10, 0xFFFFFF
        );

        // 语言文件里的换行可能是 \n 字面量，这里统一兜底成真换行
        String raw = Component.translatable("cir.gift.disclaimer.body").getString()
            .replace("\\n", "\n");

        int y = top + 28;
        for (String paragraph : raw.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                y += 6;
                continue;
            }
            for (String line : this.wrap(paragraph, PANEL_WIDTH - 40)) {
                graphics.drawString(this.font, line, left + 20, y, 0xC8C8C8, false);
                y += 10;
            }
        }

        // 落款：右下角，与正文保持一段距离
        String signature = Component.translatable("cir.gift.disclaimer.signature").getString()
            .replace("\\n", "\n");
        int sigY = y + 14;
        for (String line : signature.split("\n", -1)) {
            if (line.isEmpty()) {
                sigY += 4;
                continue;
            }
            int w = this.font.width(line);
            graphics.drawString(this.font, line, left + PANEL_WIDTH - 20 - w, sigY, 0xA8A8A8, false);
            sigY += 10;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
