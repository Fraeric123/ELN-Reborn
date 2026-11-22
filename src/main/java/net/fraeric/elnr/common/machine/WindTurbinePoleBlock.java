package net.fraeric.elnr.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Pole of a wind turbine. One layer of the pole is a square of blocks around the centre block
 * the player placed (the extra blocks appear and vanish on their own) and behaves as one big
 * block: it is drawn as one tapering segment, breaks as one and is one electrical device.
 * The layers get thinner from the bottom to the top whatever the height of the tower.
 */
public class WindTurbinePoleBlock extends Block implements EntityBlock, ElectricMachineBlock {

    /** Width of the layer: 4 + 2 * w pixels (4 .. 80). Set automatically. */
    public static final IntegerProperty WIDTH = IntegerProperty.create("w", 0, 38);
    /** Offset of this block from the centre block of its layer, plus 2 (2 = this is the centre). */
    public static final IntegerProperty OX = IntegerProperty.create("ox", 0, 4);
    public static final IntegerProperty OZ = IntegerProperty.create("oz", 0, 4);
    private static final VoxelShape[][][] SHAPES = new VoxelShape[39][5][5];

    static {
        for (int w = 0; w <= 38; w++) {
            for (int ox = 0; ox < 5; ox++) {
                for (int oz = 0; oz < 5; oz++) {
                    double width = width(w);
                    int dx = ox - 2;
                    int dz = oz - 2;
                    SHAPES[w][ox][oz] = covers(dx, width) && covers(dz, width)
                            ? Block.box(lo(dx, width), 0.0D, lo(dz, width), hi(dx, width), 16.0D, hi(dz, width))
                            : Shapes.empty();
                }
            }
        }
    }

    public static double width(int w) {
        return 4.0D + 2.0D * w;
    }

    /** Start / end (in pixels, inside the block {@code d} blocks from the centre) of a layer of the given width. */
    public static double lo(int d, double width) {
        return Math.max(0.0D, 8.0D - width / 2.0D - 16.0D * d);
    }

    public static double hi(int d, double width) {
        return Math.min(16.0D, 8.0D + width / 2.0D - 16.0D * d);
    }

    public static boolean covers(int d, double width) {
        return hi(d, width) - lo(d, width) > 0.01D;
    }

    public static boolean isCenter(BlockState state) {
        return state.getValue(OX) == 2 && state.getValue(OZ) == 2;
    }

    /** How far (in blocks) a cable lying beside this block has to run to touch the pole. */
    public static float reach(BlockState state) {
        double width = width(state.getValue(WIDTH));
        double recess = 1.0D;
        int[] d = {state.getValue(OX) - 2, state.getValue(OZ) - 2};
        for (int axis : d) {
            double lo = lo(axis, width);
            double hi = hi(axis, width);
            double r = axis == 0 ? Math.min(lo, 16.0D - hi) : axis > 0 ? 16.0D - hi : lo;
            recess = Math.max(recess, r);
        }
        return (float) (recess / 16.0D);
    }

    public WindTurbinePoleBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(WIDTH, 0).setValue(OX, 2).setValue(OZ, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WIDTH, OX, OZ);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(WIDTH)][state.getValue(OX)][state.getValue(OZ)];
    }

    /** The pole is drawn by the block entity of the centre block. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCenter(state) ? new WindTurbinePoleBlockEntity(pos, state) : null;
    }

    /** Right-clicking the pole shows the statistics of the rotor on top of it. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof net.fraeric.elnr.common.cable.CableItem) {
            return InteractionResult.PASS;
        }
        BlockPos turbine = WindTurbineTower.turbineOf(level, pos);
        if (turbine == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            WindTurbineBlock.showInfo(level, turbine, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && old.getBlock() != state.getBlock() && isCenter(state)) {
            WindTurbineTower.retaper(level, pos);
            WindTurbineTower.refresh(level, pos);
            // the block entity does not exist yet during placement: fix its widths a tick later
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isCenter(state)) {
            WindTurbineTower.retaper(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (level.isClientSide || state.getBlock() == newState.getBlock() || WindTurbineTower.isBusy()) {
            return;
        }
        // Breaking one block of a layer breaks the whole layer.
        BlockPos center = pos.offset(2 - state.getValue(OX), 0, 2 - state.getValue(OZ));
        WindTurbineTower.breakLayer(level, center, pos);
        WindTurbineTower.retaper(level, center.above());
        WindTurbineTower.retaper(level, center.below());
        WindTurbineTower.refresh(level, center);
    }
}
