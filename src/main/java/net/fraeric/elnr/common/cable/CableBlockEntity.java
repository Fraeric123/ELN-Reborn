package net.fraeric.elnr.common.cable;

import net.fraeric.elnr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * One block can hold up to six cable pieces (one per inner face).
 * The block entity only exists so that {@link net.fraeric.elnr.client.CableBlockRenderer}
 * has something to render; all data lives in the block state.
 */
public class CableBlockEntity extends BlockEntity {

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
    }

    /**
     * Cable strips that wrap around an outer edge poke a little over the border of
     * their block, so the renderer is not culled as soon as the block leaves the view.
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.125D);
    }
}
