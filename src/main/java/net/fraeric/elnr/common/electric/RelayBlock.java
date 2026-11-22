package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Relay: a changeover switch driven by a coil. Terminals (relative to the terminal axis {@code t}
 * and {@code side = facing x t}): common on {@code -t}, output A on {@code +t}, output B on
 * {@code +side} and the coil input on {@code -side}. While the coil is not energised the common
 * terminal is joined to A; an energised coil ({@code open=true}) joins it to B. The coil input
 * never conducts, it is only read by the block entity.
 *
 * <p>The base only fixes the switched voltage and current (the usual switch hazards apply to it);
 * the coil is a separate item kept in the block entity and swapped in the relay's menu.</p>
 */
public class RelayBlock extends SwitchBlock implements EntityBlock {

    /** 0 = no coil, 1..4 = working coil by voltage, 5 = burnt coil. Drives the coil model. */
    public static final IntegerProperty COIL = IntegerProperty.create("coil", 0, RelayCoilItem.STATE_BURNT);

    // Model frame boxes (1/16): must match relay_housing_*.json, relay_armature_*.json, relay_coil_*.json.
    private static final double[] HOUSING = {4, 0, 4, 12, 2.5, 12};
    private static final double[] PIN_COMMON = {6.5, 0, 0, 9.5, 1.5, 4};
    private static final double[] PIN_A = {6.5, 0, 12, 9.5, 1.5, 16};
    private static final double[] PIN_B = {12, 0, 6.5, 16, 1.5, 9.5};
    private static final double[] PIN_COIL = {0, 0, 7, 4, 1, 9};
    private static final double[] ARM_A = {7, 2.5, 7, 9, 4, 14.5};
    private static final double[] ARM_B = {7, 2.5, 7, 14.5, 4, 9};
    private static final double[] CAN = {5, 2.5, 5, 11, 9.5, 11};

    public RelayBlock(Properties properties, double ratedVoltage, double ratedCurrent) {
        super(properties, ratedVoltage, ratedCurrent);
        registerDefaultState(defaultBlockState().setValue(OPEN, Boolean.FALSE).setValue(COIL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COIL);
    }

    public static Direction sideOf(BlockState state) {
        return cross(state.getValue(FACING), terminal(state));
    }

    /** @return the side of the block on which the coil input sits. */
    public static Direction coilSide(BlockState state) {
        return sideOf(state).getOpposite();
    }

    @Override
    public boolean isOpen(BlockState state) {
        return false;
    }

    @Override
    public Direction[] terminalSides(BlockState state) {
        Direction t = terminal(state);
        Direction side = sideOf(state);
        return new Direction[]{t.getOpposite(), t, side, side.getOpposite()};
    }

    @Override
    public Direction[] passThrough(BlockState state, Direction entered) {
        Direction t = terminal(state);
        if (entered == coilSide(state)) {
            return new Direction[0];
        }
        Direction active = state.getValue(OPEN) ? sideOf(state) : t;
        if (entered == t.getOpposite()) {
            return new Direction[]{active};
        }
        if (entered == active) {
            return new Direction[]{t.getOpposite()};
        }
        return new Direction[0];
    }

    @Override
    protected double[][] boxes(BlockState state) {
        List<double[]> boxes = new ArrayList<>(List.of(HOUSING, PIN_COMMON, PIN_A, PIN_B, PIN_COIL,
                state.getValue(OPEN) ? ARM_B : ARM_A));
        if (state.getValue(COIL) != 0) {
            boxes.add(CAN);
        }
        return boxes.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------
    // Block entity
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.RELAY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((RelayBlockEntity) be).serverTick();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(this));
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RelayBlockEntity relay) {
            ItemStack coil = relay.getCoil();
            if (!coil.isEmpty()) {
                drops.add(coil.copy());
            }
        }
        return drops;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof CableItem) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, state.cycle(ROTATION));
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.9F);
                ElectricNetwork.updateAt(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RelayBlockEntity relay) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, p) -> new RelayMenu(id, inventory, relay),
                            Component.translatable(getDescriptionId())),
                    pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A relay is driven by its coil, not by clicking. */
    @Override
    protected void activate(BlockState state, Level level, BlockPos pos) {
    }
}
