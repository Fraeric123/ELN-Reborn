package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two terminal switch driven by redstone: closed while it receives a redstone signal
 * (from any side), open otherwise. Has no manual control.
 */
public class RedstoneSwitchBlock extends SwitchBlock {

    public RedstoneSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        return state.setValue(OPEN, !context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** Driven by redstone, not by clicking. */
    @Override
    protected void activate(BlockState state, Level level, BlockPos pos) {
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean open = !level.hasNeighborSignal(pos);
            if (open != state.getValue(OPEN)) {
                level.setBlockAndUpdate(pos, state.setValue(OPEN, open));
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, open ? 0.5F : 0.6F);
                ElectricNetwork.updateAt(level, pos);
                return;
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }
}
