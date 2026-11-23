package net.fraeric.elnr.common.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that talks to signal cables. Like every device of the mod it is a hub: it joins every
 * (non bundled) signal cable piece that touches it, whatever the colour of that piece.
 */
public interface SignalDevice {

    /** @return true while this block drives a {@code true} into the signal cables next to it. */
    default boolean emitsSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return false;
    }

    /** @return true when {@link #onSignal} has to be told the value of the cables next to this block. */
    default boolean receivesSignal(BlockState state) {
        return false;
    }

    /**
     * Called (server side) whenever the signal around this device may have changed.
     *
     * @param signal logical OR of every signal network that touches the device
     */
    default void onSignal(Level level, BlockPos pos, BlockState state, boolean signal) {
    }

    /**
     * @param face mounting face of a signal cable piece next to the device
     * @return how far (in blocks) that strip runs into the device, or a negative number when the
     * strip does not visually connect to it
     */
    float cableReach(BlockState state, Direction face);
}
