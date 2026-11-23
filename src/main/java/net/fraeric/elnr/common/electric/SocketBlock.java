package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Small bulb socket that mounts on the floor, on walls or on the ceiling.
 *
 * <p>Right-click with a bulb screws it in, right-click with a cable plugs the cable
 * straight into the socket - no cable strip has to be laid against its side. Once a
 * cable is plugged in, the socket joins every cable network of the same tier that
 * touches it.</p>
 */
public class SocketBlock extends Block implements EntityBlock {

    /** Direction the socket points away from the surface it is mounted on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Mirrors "a bulb is screwed in" so the model can show an empty socket. */
    public static final BooleanProperty HAS_BULB = BooleanProperty.create("bulb");

    /**
     * Porcelain plate and the holder cup, authored for a socket lying on the floor
     * (1/16 block units). Must match {@code socket_empty.json}: a small 6x6 px plate,
     * so a cable strip runs up to its edge 5 px from the block border.
     */
    private static final double[][] BODY = {
            {5, 0, 5, 11, 1, 11},
            {6, 1, 6, 10, 4, 10},
            {6.5, 4, 6.5, 9.5, 5.5, 9.5}
    };
    /** Screw cap and glass of the bulb, matching {@code socket.json}. */
    private static final double[][] BULB = {
            {7, 4, 7, 9, 5.5, 9},
            {5.5, 5.5, 5.5, 10.5, 12, 10.5}
    };

    public SocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(LIT, Boolean.FALSE)
                .setValue(HAS_BULB, Boolean.FALSE));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SocketBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, HAS_BULB);
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
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? 14 : 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = DeviceShapes.device(state.getValue(FACING), BODY);
        if (state.getValue(HAS_BULB)) {
            shape = Shapes.or(shape, DeviceShapes.device(state.getValue(FACING), BULB));
        }
        return shape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SocketBlockEntity socket)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new SocketMenu(id, inventory, socket),
                            net.minecraft.network.chat.Component.translatable(getDescriptionId())),
                    pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SocketBlockEntity socket) {
            if (!level.isClientSide) {
                Containers.dropContents(level, pos, socket.getInventory());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
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
}
