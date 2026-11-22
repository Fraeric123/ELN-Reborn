package net.fraeric.elnr.client;

import net.fraeric.elnr.common.electric.TimerBlockEntity;
import net.fraeric.elnr.common.electric.TimerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.fraeric.elnr.network.ModNetwork;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Timer configuration: mode button and the times in seconds. */
public class TimerScreen extends AbstractContainerScreen<TimerMenu> {

    private final NumberField[] fields = new NumberField[3];
    private Button modeButton;
    private int lastMode = -1;

    public TimerScreen(TimerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 140;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static String format(int ticks) {
        double seconds = ticks / 20.0D;
        return seconds == Math.rint(seconds) ? Long.toString((long) seconds)
                : String.format(Locale.ROOT, "%.2f", seconds);
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(TimerMenu.BTN_MODE))
                .bounds(leftPos + 10, topPos + 22, imageWidth - 20, 20).build());
        String[] labels = {"gui.elnr.timer.delay", "gui.elnr.timer.on", "gui.elnr.timer.off"};
        for (int i = 0; i < 3; i++) {
            NumberField box = new NumberField(font, leftPos + 100, topPos + 52 + (i == 2 ? 1 : 0) * 22, 70, 18,
                    Component.translatable(labels[i]), true);
            fields[i] = box;
            addRenderableWidget(box);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.set"), b -> apply())
                .bounds(leftPos + 10, topPos + 112, imageWidth - 20, 20).build());
        updateVisibility();
    }

    private void updateVisibility() {
        boolean cycle = menu.getMode() == TimerBlockEntity.MODE_CYCLE;
        fields[0].visible = !cycle;
        fields[1].visible = cycle;
        fields[2].visible = cycle;
        modeButton.setMessage(Component.translatable(cycle ? "gui.elnr.timer.mode_cycle" : "gui.elnr.timer.mode_delay"));
        lastMode = menu.getMode();
    }

    private void apply() {
        boolean cycle = menu.getMode() == TimerBlockEntity.MODE_CYCLE;
        int[] ticks = new int[3];
        for (int i = 0; i < 3; i++) {
            ticks[i] = menu.getTicks(i);
            boolean shown = i == 0 ? !cycle : cycle;
            double seconds = fields[i].number();
            if (shown && !Double.isNaN(seconds)) {
                ticks[i] = (int) Math.max(1, Math.min(TimerBlockEntity.MAX_TICKS, Math.round(seconds * 20.0D)));
                fields[i].markApplied();
            }
        }
        ModNetwork.sendValues(ticks);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.getMode() != lastMode) {
            updateVisibility();
        }
        for (int i = 0; i < 3; i++) {
            fields[i].sync(menu.getTicks(i), format(menu.getTicks(i)));
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
        g.drawString(font, title, 10, 8, 0x404040, false);
        boolean cycle = menu.getMode() == TimerBlockEntity.MODE_CYCLE;
        if (cycle) {
            g.drawString(font, Component.translatable("gui.elnr.timer.on"), 10, 52 + 5, 0x404040, false);
            g.drawString(font, Component.translatable("gui.elnr.timer.off"), 10, 74 + 5, 0x404040, false);
        } else {
            g.drawString(font, Component.translatable("gui.elnr.timer.delay"), 10, 52 + 5, 0x404040, false);
        }
        int rows = cycle ? 2 : 1;
        for (int i = 0; i < rows; i++) {
            g.drawString(font, "s", 176, 52 + i * 22 + 5, 0x404040, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
