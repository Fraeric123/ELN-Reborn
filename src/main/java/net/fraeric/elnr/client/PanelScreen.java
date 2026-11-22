package net.fraeric.elnr.client;

import net.fraeric.elnr.common.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Simple generic machine screen: info lines, buttons (ids 0..n) and the slots of the menu. */
public class PanelScreen extends AbstractContainerScreen<MachineMenu> {

    private final List<Button> buttons = new ArrayList<>();

    public PanelScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        List<Component> labels = menu.buttonLabels();
        for (int i = 0; i < labels.size(); i++) {
            final int id = i;
            Button button = Button.builder(labels.get(i), b -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                }
            }).bounds(labels.size() > 2 ? leftPos + 8 + (i % 3) * 56 : leftPos + 8 + i * 84,
                            labels.size() > 2 ? topPos + 40 + (i / 3) * 16 : topPos + 56,
                            labels.size() > 2 ? 54 : 80, 14).build();
            buttons.add(button);
            addRenderableWidget(button);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
        for (Slot slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF8B8B8B);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF373737);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        int y = 18;
        for (Component line : menu.infoLines()) {
            g.drawString(font, line, menu.slots.size() > 36 ? 8 : 8, y, 0x303030, false);
            y += 10;
        }
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
