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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    /** Quarter turns of the display's "up" direction around the mounting normal (same as the switches). */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    /** Panel height above the support, in 1/16 block. The display sits on top of it. */
    public static final double DEPTH = 2.0D;
    private static final double[] BOX = {2, 0, 4, 14, DEPTH, 12};

    private final Kind kind;

    public MeterBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ROTATION, 0));
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
        builder.add(FACING, ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        // Text reads upwards on walls; on floors and ceilings it runs away from the player.
        Direction wanted = facing.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection() : Direction.UP;
        int best = 0;
        int bestDot = Integer.MIN_VALUE;
        for (int rotation = 0; rotation < 4; rotation++) {
            Direction t = SwitchBlock.terminalDirection(facing, rotation);
            int dot = t.getStepX() * wanted.getStepX() + t.getStepY() * wanted.getStepY()
                    + t.getStepZ() * wanted.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = rotation;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(ROTATION, best);
    }

    /** Direction the display text points "up" to. */
    public static Direction up(BlockState state) {
        return SwitchBlock.terminalDirection(state.getValue(FACING), state.getValue(ROTATION));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof net.fraeric.elnr.common.cable.CableItem) {
            return InteractionResult.PASS;
        }
        // Like the switches: sneak + empty hand turns it.
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, state.cycle(ROTATION));
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
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
        Direction facing = state.getValue(FACING);
        Direction terminal = up(state);
        Direction side = SwitchBlock.cross(facing, terminal);
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int corner = 0; corner < 8; corner++) {
            double mx = (corner & 1) == 0 ? BOX[0] : BOX[3];
            double my = (corner & 2) == 0 ? BOX[1] : BOX[4];
            double mz = (corner & 4) == 0 ? BOX[2] : BOX[5];
            double[] w = {
                    8 - facing.getStepX() * 8 + facing.getStepX() * my + terminal.getStepX() * (mz - 8) + side.getStepX() * (mx - 8),
                    8 - facing.getStepY() * 8 + facing.getStepY() * my + terminal.getStepY() * (mz - 8) + side.getStepY() * (mx - 8),
                    8 - facing.getStepZ() * 8 + facing.getStepZ() * my + terminal.getStepZ() * (mz - 8) + side.getStepZ() * (mx - 8)};
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], w[axis]);
                max[axis] = Math.max(max[axis], w[axis]);
            }
        }
        return Shapes.box(min[0] / 16.0D, min[1] / 16.0D, min[2] / 16.0D, max[0] / 16.0D, max[1] / 16.0D, max[2] / 16.0D);
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
