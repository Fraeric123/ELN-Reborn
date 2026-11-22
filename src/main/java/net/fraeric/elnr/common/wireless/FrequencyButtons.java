package net.fraeric.elnr.common.wireless;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Button ids are sent as a byte, so the frequency is edited with +/- steps. */
public final class FrequencyButtons {

    public static final int[] DELTAS = {-100, -10, -1, 1, 10, 100};

    private FrequencyButtons() {
    }

    public static List<Component> labels() {
        return List.of(Component.literal("-100"), Component.literal("-10"), Component.literal("-1"),
                Component.literal("+1"), Component.literal("+10"), Component.literal("+100"));
    }

    /** @return the new frequency, or -1 for an unknown button id. */
    public static int apply(int frequency, int id) {
        if (id < 0 || id >= DELTAS.length) {
            return -1;
        }
        return Math.max(0, Math.min(FrequencyHolder.MAX_FREQUENCY, frequency + DELTAS[id]));
    }
}
