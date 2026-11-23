package net.fraeric.elnr.client;

import net.fraeric.elnr.common.machine.GravityBlockEntity;
import net.fraeric.elnr.common.machine.GravityMenu;
import net.fraeric.elnr.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Settings of the gravity generator: min / max gravity (% of normal), range of the column, mode, direction. */
public class GravityScreen extends AbstractContainerScreen<GravityMenu> {

    private static final String[] KEYS = {"min", "max", "distance"};
    private static final String[] UNITS = {"%", "%", "m"};
    private static final String[] MODES = {"pull", "push", "zero"};

    private final NumberField[] fields = new NumberField[3];
    private Button modeButton;
    private Button facingButton;

    public GravityScreen(GravityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 3; i++) {
            fields[i] = new NumberField(font, leftPos + 92, topPos + 54 + i * 20, 62, 18,
                    Component.translatable("gui.elnr.gravity." + KEYS[i]), false);
            addRenderableWidget(fields[i]);
        }
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(0))
                .bounds(leftPos + 10, topPos + 118, imageWidth - 20, 18).build());
        facingButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(1))
                .bounds(leftPos + 10, topPos + 138, imageWidth - 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.set"), b -> apply())
                .bounds(leftPos + 10, topPos + 160, imageWidth - 20, 20).build());
        updateLabels();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void apply() {
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            double n = fields[i].number();
            values[i] = Double.isNaN(n) ? menu.get(i) : (int) Math.round(n);
            fields[i].markApplied();
        }
        ModNetwork.sendValues(values);
    }

    private void updateLabels() {
        if (modeButton == null) {
            return;
        }
        modeButton.setMessage(Component.translatable("gui.elnr.gravity.mode",
                Component.translatable("gui.elnr.gravity.mode." + MODES[Math.max(0, Math.min(2, menu.get(3)))])));
        Direction dir = Direction.from3DDataValue(Math.max(0, Math.min(5, menu.get(8))));
        facingButton.setMessage(Component.translatable("gui.elnr.gravity.facing",
                Component.translatable("gui.elnr.gravity.dir." + dir.getName())));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < 3; i++) {
            fields[i].sync(menu.get(i), Integer.toString(menu.get(i)));
        }
        updateLabels();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (NumberField.handleKey(fields, key, scan, modifiers, this::apply)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        Component status;
        int mode = menu.get(3);
        if (menu.get(5) == 0) {
            status = Component.translatable("gui.elnr.gravity.status_off");
        } else if (mode == GravityBlockEntity.MODE_ZERO) {
            status = Component.translatable("gui.elnr.gravity.status_zero");
        } else {
            status = Component.translatable("gui.elnr.gravity.status_on", Math.abs(menu.get(4)));
        }
        g.drawString(font, status, 8, 18, menu.get(5) == 0 ? 0xAA0000 : 0x007700, false);
        g.drawString(font, Component.translatable("gui.elnr.gravity.power", menu.get(6)), 8, 30, 0x303030, false);
        g.drawString(font, Component.translatable("gui.elnr.gravity.group", menu.get(7)), 8, 40, 0x303030, false);
        for (int i = 0; i < 3; i++) {
            g.drawString(font, Component.translatable("gui.elnr.gravity." + KEYS[i]), 10, 54 + i * 20 + 5, 0x404040, false);
            g.drawString(font, UNITS[i], 158, 54 + i * 20 + 5, 0x404040, false);
        }
    }
}
