package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Wind turbine: the higher it stands, the faster it spins and the more power it makes. */
public class WindTurbineBlock extends MachineBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final java.util.Map<Direction, VoxelShape> SHAPES = new java.util.EnumMap<>(Direction.class);

    static {
        // boxes of a turbine facing north: pole stub, body, nose
        double[][] boxes = {{6, 0, 6, 10, 5, 10}, {5, 5, 3, 11, 11, 13}, {6.5, 6.5, 1, 9.5, 9.5, 3}};
        Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int turns = 0; turns < 4; turns++) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
                for (int i = 0; i < turns; i++) {
                    double nx1 = 16 - z2, nx2 = 16 - z1, nz1 = x1, nz2 = x2;
                    x1 = nx1; x2 = nx2; z1 = nz1; z2 = nz2;
                }
                shape = Shapes.or(shape, Block.box(x1, b[1], z1, x2, b[4], z2));
            }
            SHAPES.put(order[turns], shape);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (old.getBlock() != state.getBlock()) {
            WindTurbineTower.refresh(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (state.getBlock() != newState.getBlock()) {
            WindTurbineTower.refresh(level, pos);
        }
    }

    public WindTurbineBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindTurbineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModMachines.WIND_TURBINE_BE.get(), WindTurbineBlockEntity::tick);
    }

    private static String formatWatts(double w) {
        if (w >= 1.0E9D) return String.format(Locale.ROOT, "%.2f GW", w / 1.0E9D);
        if (w >= 1.0E6D) return String.format(Locale.ROOT, "%.2f MW", w / 1.0E6D);
        if (w >= 1.0E3D) return String.format(Locale.ROOT, "%.1f kW", w / 1.0E3D);
        return String.format(Locale.ROOT, "%.0f W", w);
    }

    /** Shows wind, rotor speed, output and pole height of the turbine at {@code pos}. */
    public static void showInfo(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof WindTurbineBlockEntity be) {
            player.displayClientMessage(Component.translatable("message.elnr.wind_turbine.info",
                    String.format(Locale.ROOT, "%.1f", be.getWind()),
                    String.format(Locale.ROOT, "%.1f", be.getRotorRads() * 60.0D / (2 * Math.PI)),
                    formatWatts(be.getAvailableWatts()),
                    (int) WindTurbineBlockEntity.VOLTAGE,
                    be.getPoleHeight()), true);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (isCable(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            showInfo(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
