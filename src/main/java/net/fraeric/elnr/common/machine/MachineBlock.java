package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Common behaviour of the new machine blocks: network updates and inventory drops. */
public abstract class MachineBlock extends BaseEntityBlock implements ElectricMachineBlock {

    protected MachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && old.getBlock() != state.getBlock()) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof MachineInventory inv) {
                Containers.dropContents(level, pos, (Container) inv.getInventory());
            }
            super.onRemove(state, level, pos, newState, moving);
            if (!level.isClientSide) {
                ElectricNetwork.updateAt(level, pos);
            }
            return;
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    protected static boolean isCable(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof CableItem;
    }
}
