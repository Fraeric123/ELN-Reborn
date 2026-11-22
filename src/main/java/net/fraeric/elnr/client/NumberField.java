package net.fraeric.elnr.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Number input. It shows the value the block currently has: {@link #sync} pushes the synced
 * value into the box whenever it changes, unless the player is in the middle of typing.
 */
public class NumberField extends EditBox {

    private boolean edited = false;
    private boolean programmatic = false;
    private int shown = Integer.MIN_VALUE;

    public NumberField(Font font, int x, int y, int width, int height, Component label, boolean decimals) {
        super(font, x, y, width, height, label);
        setMaxLength(decimals ? 8 : 5);
        setFilter(text -> text.matches(decimals ? "\\d{0,5}([.,]\\d{0,2})?" : "\\d{0,5}"));
        setResponder(text -> {
            if (!programmatic) {
                edited = true;
            }
        });
    }

    /** @param raw the synced raw value; the text is only replaced when it changed or was never edited */
    public void sync(int raw, String text) {
        if (raw == shown) {
            return;
        }
        boolean first = shown == Integer.MIN_VALUE;
        shown = raw;
        if (first || !edited || !isFocused()) {
            programmatic = true;
            setValue(text);
            programmatic = false;
            edited = false;
        }
    }

    public void markApplied() {
        edited = false;
    }

    /** @return the typed number, or NaN when empty. */
    public double number() {
        try {
            return Double.parseDouble(getValue().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Enter applies, other keys go to the focused box (so E does not close the screen). */
    public static boolean handleKey(NumberField[] boxes, int key, int scan, int modifiers, Runnable apply) {
        for (NumberField box : boxes) {
            if (box.isFocused() && box.visible) {
                if (key == 257 || key == 335) {
                    apply.run();
                    return true;
                }
                if (key != 256) {
                    box.keyPressed(key, scan, modifiers);
                    return true;
                }
            }
        }
        return false;
    }
}
