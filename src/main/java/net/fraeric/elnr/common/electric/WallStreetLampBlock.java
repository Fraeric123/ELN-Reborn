package net.fraeric.elnr.common.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Wall-mounted variant of the street lamp: one block with a bracket and a lantern. It is a street lamp
 * whose base and head are the same block, so bulb, cable and network behave exactly like the tall one.
 */
public class WallStreetLampBlock extends StreetLampBlock {

    /** Direction the lamp points away from the wall. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Hangs from the ceiling instead of standing off a wall. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CEILING =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("ceiling");

    /** Ceiling variant, matches wall_street_lamp_ceiling.json. */
    private static final double[][] CEILING_BOXES = {
            {5, 15, 5, 11, 16, 11}, {6.5, 14, 6.5, 9.5, 15, 9.5}, {7.5, 10.75, 7.5, 8.5, 14, 8.5},
            {5, 9.5, 5, 11, 10.9, 11}, {5, 2, 5, 11, 9.5, 11}, {7.5, 0.5, 7.5, 8.5, 2, 8.5}};

    /** Boxes for facing=south (wall at z=0), in 1/16 block units; must match wall_street_lamp.json. */
    private static final double[][] BOXES = {
            {5, 2, 0, 11, 14, 1}, {7, 6, 1, 9, 10, 2.5}, {7.25, 5.5, 2.5, 8.75, 8.25, 10},
            {6.5, 6.75, 7.5, 9.5, 14, 12.5}, {6, 14, 7, 10, 14.75, 13}, {7, 14.75, 8, 9, 16, 12}};

    public WallStreetLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(CEILING, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, CEILING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) {
            return null;
        }
        BlockState state = face == Direction.DOWN
                ? defaultBlockState().setValue(CEILING, Boolean.TRUE).setValue(FACING, context.getHorizontalDirection().getOpposite())
                : defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // single block: nothing to add
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(CEILING)) {
            return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN);
        }
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(CEILING)) {
            VoxelShape hanging = Shapes.empty();
            for (double[] b : CEILING_BOXES) {
                hanging = Shapes.or(hanging, Shapes.box(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16));
            }
            return hanging;
        }
        Direction f = state.getValue(FACING);
        VoxelShape shape = Shapes.empty();
        for (double[] b : BOXES) {
            double x1, z1, x2, z2;
            switch (f) {
                case WEST -> { x1 = 16 - b[5]; z1 = b[0]; x2 = 16 - b[2]; z2 = b[3]; }
                case NORTH -> { x1 = 16 - b[3]; z1 = 16 - b[5]; x2 = 16 - b[0]; z2 = 16 - b[2]; }
                case EAST -> { x1 = b[2]; z1 = 16 - b[3]; x2 = b[5]; z2 = 16 - b[0]; }
                default -> { x1 = b[0]; z1 = b[2]; x2 = b[3]; z2 = b[5]; }
            }
            shape = Shapes.or(shape, Shapes.box(x1 / 16, b[1] / 16, z1 / 16, x2 / 16, b[4] / 16, z2 / 16));
        }
        return shape;
    }
}
