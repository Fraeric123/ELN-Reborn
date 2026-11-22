package net.fraeric.elnr.client;

import net.fraeric.elnr.common.electric.RelayBlock;
import net.fraeric.elnr.common.electric.RelayCoilItem;
import net.fraeric.elnr.common.electric.RelayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;

/** Relay: shows the base rating, the coil slot and the state of the coil. */
public class RelayScreen extends AbstractContainerScreen<RelayMenu> {

    public RelayScreen(RelayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
        // Highlight of the coil slot in the colour of the coil state.
        int state = menu.getCoilState();
        int color = menu.isEnergised() ? 0xFF55FF55 : 0xFF555555;
        if (state == RelayCoilItem.STATE_BURNT) {
            color = 0xFFAA2222;
        }
        int x = leftPos + RelayMenu.COIL_X;
        int y = topPos + RelayMenu.COIL_Y;
        g.fill(x - 3, y - 3, x + 19, y - 2, color);
        g.fill(x - 3, y + 18, x + 19, y + 19, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        if (minecraft != null && minecraft.level != null) {
            BlockState block = minecraft.level.getBlockState(menu.getPos());
            if (block.getBlock() instanceof RelayBlock relay) {
                g.drawString(font, Component.translatable("gui.elnr.relay.rating",
                        (int) relay.getRatedVoltage(), (int) relay.getRatedCurrent()), 8, 18, 0x404040, false);
            }
        }
        g.drawString(font, Component.translatable("gui.elnr.relay.coil"), 8, 34, 0x404040, false);
        int state = menu.getCoilState();
        Component status;
        if (state == 0) {
            status = Component.translatable("gui.elnr.relay.no_coil");
        } else if (state == RelayCoilItem.STATE_BURNT) {
            status = Component.translatable("gui.elnr.relay.burnt");
        } else {
            status = Component.translatable(menu.isEnergised() ? "gui.elnr.relay.on" : "gui.elnr.relay.off",
                    RelayCoilItem.VOLTAGES[state - 1]);
        }
        g.drawString(font, status, 8, 52, state == RelayCoilItem.STATE_BURNT ? 0xAA0000 : 0x404040, false);
        g.drawString(font, Component.translatable("gui.elnr.relay.measured", String.format("%.1f", menu.getMeasured())),
                8, 62, 0x404040, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
