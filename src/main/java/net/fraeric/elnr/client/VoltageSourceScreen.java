package net.fraeric.elnr.client;

import net.fraeric.elnr.common.electric.VoltageSourceBlock;
import net.fraeric.elnr.common.electric.VoltageSourceMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Primitive screen: one number field for the output voltage and a Set button. */
public class VoltageSourceScreen extends AbstractContainerScreen<VoltageSourceMenu> {

    private NumberField input;

    public VoltageSourceScreen(VoltageSourceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 70;
    }

    @Override
    protected void init() {
        super.init();
        input = new NumberField(font, leftPos + 12, topPos + 28, 100, 18, Component.translatable("block.elnr.voltage_source"), true);
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.set"), b -> apply())
                .bounds(leftPos + 118, topPos + 27, 46, 20).build());
    }

    private void apply() {
        double value = input.number();
        if (!Double.isNaN(value)) {
            value = VoltageSourceMenu.clamp(value);
            net.fraeric.elnr.network.ModNetwork.sendValues(VoltageSourceMenu.toData(value));
            input.markApplied();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        input.sync((int) Math.round(menu.getVoltage() * 100.0D), VoltageSourceBlock.formatVoltage(menu.getVoltage()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (NumberField.handleKey(new NumberField[]{input}, key, scan, modifiers, this::apply)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 8, 0x404040, false);
        g.drawString(font, Component.translatable("gui.elnr.voltage_hint", (int) VoltageSourceMenu.MAX_VOLTAGE), 12, 52, 0x606060, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
