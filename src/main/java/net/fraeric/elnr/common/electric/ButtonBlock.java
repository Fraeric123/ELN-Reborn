package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Momentary push button: same footprint and terminals as the light switch, it
 * closes while pressed and springs open again after a second.
 */
public class ButtonBlock extends SwitchBlock {

    private static final int PRESS_TICKS = 20;
    /** Cap in the model frame: raised, and pressed in. */
    private static final double[] CAP_RELEASED = {7, 1.5, 6.5, 9, 3.5, 9.5};
    private static final double[] CAP_PRESSED = {7, 1.5, 6.5, 9, 2.25, 9.5};

    public ButtonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected double[][] boxes(BlockState state) {
        return new double[][]{BOX_CASE, state.getValue(OPEN) ? CAP_RELEASED : CAP_PRESSED, BOX_PIN_A, BOX_PIN_B};
    }

    @Override
    protected void activate(BlockState state, Level level, BlockPos pos) {
        if (!state.getValue(OPEN)) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(OPEN, Boolean.FALSE));
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3F, 0.6F);
        level.scheduleTick(pos, this, PRESS_TICKS);
        ElectricNetwork.updateAt(level, pos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) {
            level.setBlockAndUpdate(pos, state.setValue(OPEN, Boolean.TRUE));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3F, 0.6F);
            ElectricNetwork.updateAt(level, pos);
        }
    }
}
