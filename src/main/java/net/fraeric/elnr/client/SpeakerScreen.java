package net.fraeric.elnr.client;

import net.fraeric.elnr.common.electric.SpeakerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Configuration screen of the sound blocks: sound list and three on/off options. */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {

    private final List<Button> soundButtons = new ArrayList<>();
    private Button repeatButton;
    private Button interruptButton;
    private Button stopButton;

    public SpeakerScreen(SpeakerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        int sounds = menu.getKind().sounds().length;
        this.imageHeight = 24 + (sounds > 1 ? sounds * 16 + 6 : 0) + 4 * 22 + 8;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        super.init();
        soundButtons.clear();
        int y = topPos + 22;
        String[] sounds = menu.getKind().sounds();
        if (sounds.length > 1) {
            for (int i = 0; i < sounds.length; i++) {
                final int id = SpeakerMenu.BTN_SOUND + i;
                Button b = Button.builder(Component.empty(), button -> press(id))
                        .bounds(leftPos + 10, y, imageWidth - 20, 14).build();
                soundButtons.add(b);
                addRenderableWidget(b);
                y += 16;
            }
            y += 6;
        }
        repeatButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(SpeakerMenu.BTN_REPEAT))
                .bounds(leftPos + 10, y, imageWidth - 20, 20).build());
        y += 22;
        interruptButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(SpeakerMenu.BTN_INTERRUPT))
                .bounds(leftPos + 10, y, imageWidth - 20, 20).build());
        y += 22;
        stopButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(SpeakerMenu.BTN_STOP))
                .bounds(leftPos + 10, y, imageWidth - 20, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.elnr.speaker.test"), b -> press(SpeakerMenu.BTN_TEST))
                .bounds(leftPos + 10, y, imageWidth - 20, 20).build());
        refresh();
    }

    private void refresh() {
        String[] sounds = menu.getKind().sounds();
        for (int i = 0; i < soundButtons.size(); i++) {
            Component name = Component.translatable("sound.elnr." + sounds[i]);
            soundButtons.get(i).setMessage(i == menu.getSound()
                    ? Component.literal("> ").append(name).append(" <") : name);
        }
        repeatButton.setMessage(Component.translatable(menu.isRepeat()
                ? "gui.elnr.speaker.repeat" : "gui.elnr.speaker.once"));
        interruptButton.setMessage(Component.translatable(menu.isInterrupt()
                ? "gui.elnr.speaker.interrupt" : "gui.elnr.speaker.stack"));
        stopButton.setMessage(Component.translatable(menu.isStopOnOff()
                ? "gui.elnr.speaker.stop_off" : "gui.elnr.speaker.finish_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 10, 8, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
