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

    private EditBox input;

    public VoltageSourceScreen(VoltageSourceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 70;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 12, topPos + 28, 100, 18, Component.translatable("block.elnr.voltage_source"));
        input.setMaxLength(8);
        input.setFilter(text -> text.matches("\\d{0,4}([.,]\\d{0,2})?"));
        input.setValue(VoltageSourceBlock.formatVoltage(menu.getVoltage()));
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.set"), b -> apply())
                .bounds(leftPos + 118, topPos + 27, 46, 20).build());
    }

    private void apply() {
        try {
            double value = Double.parseDouble(input.getValue().replace(',', '.'));
            value = VoltageSourceMenu.clamp(value);
            int id = VoltageSourceMenu.toData(value);
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }
            input.setValue(VoltageSourceBlock.formatVoltage(value));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (input.isFocused() && (key == 257 || key == 335)) {
            apply();
            return true;
        }
        if (input.isFocused() && key != 256) {
            return input.keyPressed(key, scan, modifiers) || true;
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
