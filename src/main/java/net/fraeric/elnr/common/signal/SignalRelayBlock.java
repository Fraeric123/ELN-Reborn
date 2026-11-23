package net.fraeric.elnr.common.signal;

import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two terminal switch driven by a signal cable: closed while any signal cable touching it carries
 * {@code true}, open otherwise. The switched circuit uses the two terminals like every other switch;
 * the signal is picked up by any signal cable piece lying against the block (it is a hub, so the
 * colour of the cable does not matter). It has no manual control.
 */
public class SignalRelayBlock extends SwitchBlock implements SignalDevice {

    /** How far a signal strip on the mounting surface runs into the case of the relay, in blocks. */
    private static final float REACH = 5.0F / 16.0F;

    public SignalRelayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        return state.setValue(OPEN, !SignalNetwork.read(context.getLevel(), context.getClickedPos()));
    }

    /** Driven by the signal, not by clicking. */
    @Override
    protected void activate(BlockState state, Level level, BlockPos pos) {
    }

    @Override
    public boolean receivesSignal(BlockState state) {
        return true;
    }

    @Override
    public void onSignal(Level level, BlockPos pos, BlockState state, boolean signal) {
        boolean open = !signal;
        if (open != state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, open ? 0.5F : 0.6F);
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public float cableReach(BlockState state, Direction face) {
        return mountFace(state) == face ? REACH : -1.0F;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && oldState.getBlock() != state.getBlock()) {
            ElectricNetwork.updateAt(level, pos);
        }
    }
}
