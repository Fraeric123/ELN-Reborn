package net.fraeric.elnr.client;

import net.fraeric.elnr.common.electric.LimiterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Settings of the limiter: min / max voltage and current with step buttons. */
public class LimiterScreen extends AbstractContainerScreen<LimiterMenu> {

    private static final String[] KEYS = {"min_voltage", "max_voltage", "min_current", "max_current"};
    private static final String[] UNITS = {"V", "V", "A", "A"};
    private static final String[] LABELS = {"-100", "-10", "-1", "+1", "+10", "+100"};

    public LimiterScreen(LimiterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
    }

    @Override
    protected void init() {
        super.init();
        for (int field = 0; field < 4; field++) {
            for (int step = 0; step < 6; step++) {
                final int id = field * 10 + step;
                addRenderableWidget(Button.builder(Component.literal(LABELS[step]), b -> press(id))
                        .bounds(leftPos + 7 + step * 27, topPos + 31 + field * 28, 26, 14).build());
            }
        }
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        for (int field = 0; field < 4; field++) {
            g.drawString(font, Component.translatable("gui.elnr.limiter." + KEYS[field])
                            .append(": " + menu.getLimit(field) + " " + UNITS[field]),
                    8, 20 + field * 28, 0x404040, false);
        }
        g.drawString(font, Component.translatable(menu.isClosed() ? "gui.elnr.limiter.closed" : "gui.elnr.limiter.open"),
                8, 134, menu.isClosed() ? 0x007700 : 0xAA0000, false);
        g.drawString(font, Component.translatable("gui.elnr.limiter.measured",
                String.format(java.util.Locale.ROOT, "%.1f", menu.getVoltage()),
                String.format(java.util.Locale.ROOT, "%.2f", menu.getCurrent())), 8, 147, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
