package net.fraeric.elnr.client;

import net.fraeric.elnr.common.electric.LimiterMenu;
import net.fraeric.elnr.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Settings of the limiter: min / max voltage and current as number inputs. */
public class LimiterScreen extends AbstractContainerScreen<LimiterMenu> {

    private static final String[] KEYS = {"min_voltage", "max_voltage", "min_current", "max_current"};
    private static final String[] UNITS = {"V", "V", "A", "A"};

    private final NumberField[] fields = new NumberField[4];

    public LimiterScreen(LimiterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 4; i++) {
            fields[i] = new NumberField(font, leftPos + 92, topPos + 20 + i * 22, 62, 18,
                    Component.translatable("gui.elnr.limiter." + KEYS[i]), false);
            addRenderableWidget(fields[i]);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.set"), b -> apply())
                .bounds(leftPos + 10, topPos + 142, imageWidth - 20, 20).build());
    }

    private void apply() {
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            double n = fields[i].number();
            values[i] = Double.isNaN(n) ? menu.getLimit(i) : (int) Math.round(n);
            fields[i].markApplied();
        }
        ModNetwork.sendValues(values);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < 4; i++) {
            fields[i].sync(menu.getLimit(i), Integer.toString(menu.getLimit(i)));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (NumberField.handleKey(fields, key, scan, modifiers, this::apply)) {
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
        g.drawString(font, title, 8, 6, 0x404040, false);
        for (int i = 0; i < 4; i++) {
            g.drawString(font, Component.translatable("gui.elnr.limiter." + KEYS[i]), 10, 20 + i * 22 + 5, 0x404040, false);
            g.drawString(font, UNITS[i], 158, 20 + i * 22 + 5, 0x404040, false);
        }
        g.drawString(font, Component.translatable(menu.isClosed() ? "gui.elnr.limiter.closed" : "gui.elnr.limiter.open"),
                10, 110, menu.isClosed() ? 0x007700 : 0xAA0000, false);
        g.drawString(font, Component.translatable("gui.elnr.limiter.measured",
                String.format(Locale.ROOT, "%.1f", menu.getVoltage()),
                String.format(Locale.ROOT, "%.2f", menu.getCurrent())), 10, 124, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
