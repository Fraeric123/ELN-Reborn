package net.fraeric.elnr.client;

import net.fraeric.elnr.common.machine.MachineMenu;
import net.fraeric.elnr.common.wireless.FrequencyHolder;
import net.fraeric.elnr.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Frequency of the wireless switch, transmitter and remote control as a number input. */
public class FrequencyScreen extends AbstractContainerScreen<MachineMenu> {

    private NumberField field;

    public FrequencyScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        field = new NumberField(font, leftPos + 10, topPos + 44, 70, 18,
                Component.translatable("gui.elnr.wireless.frequency_label"), false);
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.set"), b -> apply())
                .bounds(leftPos + 88, topPos + 43, 60, 20).build());
    }

    private void apply() {
        double n = field.number();
        if (!Double.isNaN(n)) {
            ModNetwork.sendValues((int) Math.max(0, Math.min(FrequencyHolder.MAX_FREQUENCY, Math.round(n))));
            field.markApplied();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        field.sync(menu.get(0), Integer.toString(menu.get(0)));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (NumberField.handleKey(new NumberField[]{field}, key, scan, modifiers, this::apply)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
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
            g.drawString(font, line, 8, y, 0x303030, false);
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
