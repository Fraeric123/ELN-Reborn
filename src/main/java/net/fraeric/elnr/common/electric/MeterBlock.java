package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Wall (or floor / ceiling) mounted panel meter. Put it next to a cable and it shows the
 * voltage, current or power of that cable network on its display.
 */
public class MeterBlock extends Block implements EntityBlock, NetworkListener {

    public enum Kind {
        VOLTAGE("V"), CURRENT("A"), POWER("W");

        public final String unit;

        Kind(String unit) {
            this.unit = unit;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Panel height above the support, in 1/16 block. The display sits on top of it. */
    public static final double DEPTH = 2.0D;
    private static final double[] BOX = {2, 0, 4, 14, DEPTH, 12};

    private final Kind kind;

    public MeterBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public Kind getKind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeterBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DeviceShapes.device(state.getValue(FACING), BOX);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void onNetwork(Level level, BlockPos pos, double voltage, double current) {
        if (!(level.getBlockEntity(pos) instanceof MeterBlockEntity meter)) {
            return;
        }
        double value = switch (kind) {
            case VOLTAGE -> Math.abs(voltage);
            case CURRENT -> Math.abs(current);
            case POWER -> Math.abs(voltage * current);
        };
        meter.setValue(value);
    }
}
