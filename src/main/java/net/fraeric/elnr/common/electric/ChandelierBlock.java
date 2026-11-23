package net.fraeric.elnr.common.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Ceiling chandelier with six bulbs. It is a street lamp whose base and head are one block; open it to
 * change the six bulbs and the cable. Every working bulb draws its own current.
 */
public class ChandelierBlock extends StreetLampBlock {

    /** Boxes in 1/16 block units; must match chandelier.json. */
    private static final double[][] BOXES = {
            {6, 15, 6, 10, 16, 10}, {7.5, 10.5, 7.5, 8.5, 15, 8.5}, {7, 9, 7, 9, 10.5, 9},
            {7.5, 8, 6, 8.5, 9, 10}, {2, 8, 5, 14, 9, 6}, {2, 8, 10, 14, 9, 11},
            {2, 2.5, 4, 5, 8, 7}, {6.5, 2.5, 4, 9.5, 8, 7}, {11, 2.5, 4, 14, 8, 7},
            {2, 2.5, 9, 5, 8, 12}, {6.5, 2.5, 9, 9.5, 8, 12}, {11, 2.5, 9, 14, 8, 12}};
    private static final VoxelShape SHAPE;

    static {
        VoxelShape shape = Shapes.empty();
        for (double[] b : BOXES) {
            shape = Shapes.or(shape, Shapes.box(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16));
        }
        SHAPE = shape;
    }

    public ChandelierBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // single block
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
