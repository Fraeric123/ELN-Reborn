package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single solar panel block. Panel blocks touching each other (side by side on the same level)
 * form ONE panel: the output is the sum of all its blocks and a big panel has a higher voltage.
 */
public class SolarPanelBlock extends MachineBlock {

    public static final double TILE_WATTS = 25.0D;
    public static final double LOW_VOLTAGE = 50.0D;
    public static final double HIGH_VOLTAGE = 200.0D;
    /** From this many blocks the panel works at the high voltage. */
    public static final int HIGH_VOLTAGE_TILES = 9;
    private static final int MAX_TILES = 64;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2.6D, 16);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public SolarPanelBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    private static BooleanProperty prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState st = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            st = st.setValue(prop(d), ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d)).getBlock() instanceof SolarPanelBlock);
        }
        return st;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir.getAxis().isHorizontal()) {
            return state.setValue(prop(dir), neighbor.getBlock() instanceof SolarPanelBlock);
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** All panel blocks connected with {@code pos} (same level, side by side). */
    public static List<BlockPos> connected(BlockGetter level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockState(pos).getBlock() instanceof SolarPanelBlock)) {
            return out;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty() && out.size() < MAX_TILES) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof SolarPanelBlock) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return out;
    }

    public static double voltageFor(int tiles) {
        return tiles >= HIGH_VOLTAGE_TILES ? HIGH_VOLTAGE : LOW_VOLTAGE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModMachines.SOLAR_PANEL_BE.get(), SolarPanelBlockEntity::tick);
    }

    private static String formatWatts(double w) {
        if (w >= 1.0E3D) return String.format(Locale.ROOT, "%.2f kW", w / 1.0E3D);
        return String.format(Locale.ROOT, "%.0f W", w);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (isCable(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) showInfo(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Shows the stats of the whole panel that contains the block at {@code pos}. */
    public static void showInfo(Level level, BlockPos pos, Player player) {
        List<BlockPos> tiles = connected(level, pos);
        if (tiles.isEmpty()) return;
        double now = 0.0D;
        double sun = 0.0D;
        for (BlockPos p : tiles) {
            if (level.getBlockEntity(p) instanceof SolarPanelBlockEntity be) {
                now += be.getAvailableWatts();
                sun += be.getSunFactor();
            }
        }
        player.displayClientMessage(Component.translatable("message.elnr.solar_panel.info",
                formatWatts(now), formatWatts(tiles.size() * TILE_WATTS), (int) voltageFor(tiles.size()),
                Math.round(sun / tiles.size() * 100.0D)), true);
    }
}
