package net.fraeric.elnr.common.signal;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter between redstone and signal cables. Right-click switches its direction:
 *
 * <ul>
 *   <li><b>Redstone to signal</b> (default): any redstone power next to the block puts {@code true}
 *       on the signal cables touching it;</li>
 *   <li><b>Signal to redstone</b>: while a signal cable touching the block carries {@code true} the
 *       block is a redstone source of strength 15.</li>
 * </ul>
 *
 * <p>The block is a hub: it touches every signal cable piece next to it, whatever its colour.</p>
 */
public class SignalConverterBlock extends Block implements SignalDevice {

    /** false = redstone to signal, true = signal to redstone. */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");
    /** Input mode: redstone power detected (signal emitted). Output mode: redstone emitted. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Delay of the redstone output, like a repeater; keeps redstone/signal loops from recursing. */
    private static final int OUTPUT_DELAY = 2;

    public SignalConverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OUTPUT, Boolean.FALSE).setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    // ------------------------------------------------------------------
    // Redstone side
    // ------------------------------------------------------------------

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OUTPUT) && state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide || state.getValue(OUTPUT)) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            ElectricNetwork.updateAt(level, pos);
        }
    }

    // ------------------------------------------------------------------
    // Signal side
    // ------------------------------------------------------------------

    @Override
    public boolean emitsSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.getValue(OUTPUT) && state.getValue(POWERED);
    }

    @Override
    public boolean receivesSignal(BlockState state) {
        return state.getValue(OUTPUT);
    }

    @Override
    public void onSignal(Level level, BlockPos pos, BlockState state, boolean signal) {
        if (state.getValue(OUTPUT) && state.getValue(POWERED) != signal) {
            level.scheduleTick(pos, this, OUTPUT_DELAY);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OUTPUT)) {
            return;
        }
        boolean signal = SignalNetwork.read(level, pos);
        if (signal != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_ALL);
        }
    }

    @Override
    public float cableReach(BlockState state, Direction face) {
        return 0.0F;
    }

    // ------------------------------------------------------------------
    // Interaction / lifecycle
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Cables are laid against the block, not used to flip it.
        if (player.getItemInHand(hand).getItem() instanceof CableItem) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean output = !state.getValue(OUTPUT);
            boolean powered = output ? SignalNetwork.read(level, pos) : level.hasNeighborSignal(pos);
            level.setBlock(pos, state.setValue(OUTPUT, output).setValue(POWERED, powered), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, output ? 0.7F : 0.5F);
            player.displayClientMessage(Component.translatable(output
                    ? "message.elnr.signal_converter.output" : "message.elnr.signal_converter.input"), true);
            ElectricNetwork.updateAt(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && oldState.getBlock() != state.getBlock()) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ElectricNetwork.updateAt(level, pos);
        }
    }
}
