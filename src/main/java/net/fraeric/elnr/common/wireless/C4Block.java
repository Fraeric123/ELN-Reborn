package net.fraeric.elnr.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * C4 plastic explosive: detonates on any redstone signal (for example from the electric
 * redstone source), from another explosion or with flint and steel. Blast strength 6 (TNT is 4).
 */
public class C4Block extends Block implements net.fraeric.elnr.common.electric.NetworkListener {

    public static final float POWER = 6.0F;

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    /** Network voltage that sets the charge off, in volts. */
    public static final double TRIGGER_VOLTAGE = 10.0D;
    /** Small brick, authored lying on the floor (1/16 units). */
    private static final double[] BOX = {4, 0, 5, 12, 3, 11};

    public C4Block(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        net.minecraft.core.Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighborState,
                                  net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                               BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return net.fraeric.elnr.common.electric.DeviceShapes.device(state.getValue(FACING), BOX);
    }

    /** Connected to a cable network that carries voltage: boom. */
    @Override
    public void onNetwork(Level level, BlockPos pos, double voltage, double current) {
        if (Math.abs(voltage) >= TRIGGER_VOLTAGE) {
            detonate(level, pos);
        }
    }

    public static void detonate(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, POWER,
                Level.ExplosionInteraction.TNT);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) {
            net.fraeric.elnr.common.cable.ElectricNetwork.updateAt(level, pos);
            if (old.getBlock() != state.getBlock() && level.hasNeighborSignal(pos)) {
                detonate(level, pos);
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide) {
            if (level.hasNeighborSignal(pos)) {
                detonate(level, pos);
            } else {
                net.fraeric.elnr.common.cable.ElectricNetwork.updateAt(level, pos);
            }
        }
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, POWER,
                    Level.ExplosionInteraction.TNT);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        Item item = held.getItem();
        if (item != Items.FLINT_AND_STEEL && item != Items.FIRE_CHARGE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            detonate(level, pos);
            if (item == Items.FLINT_AND_STEEL) {
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            } else if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
