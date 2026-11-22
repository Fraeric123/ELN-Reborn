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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Timer: input (power) on {@code -t}, output on {@code +t} and the disable input on the
 * side. The output is only connected to the input while {@code open=false}; the disable
 * input never conducts, it is only read.
 */
public class TimerBlock extends SwitchBlock implements EntityBlock {

    private static final double[] BODY = {4, 0, 4, 12, 2.5, 12};
    private static final double[] PIN_DISABLE = {12, 0, 7, 16, 1, 9};

    public TimerBlock(Properties properties) {
        super(properties);
    }

    public static Direction disableSide(BlockState state) {
        return cross(state.getValue(FACING), terminal(state));
    }

    @Override
    public Direction[] terminalSides(BlockState state) {
        Direction t = terminal(state);
        return new Direction[]{t.getOpposite(), t, disableSide(state)};
    }

    @Override
    public Direction[] passThrough(BlockState state, Direction entered) {
        Direction t = terminal(state);
        if (state.getValue(OPEN) || entered == disableSide(state)) {
            return new Direction[0];
        }
        return new Direction[]{entered.getOpposite()};
    }

    @Override
    protected double[][] boxes(BlockState state) {
        return new double[][]{BODY, BOX_PIN_A, BOX_PIN_B, PIN_DISABLE};
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.TIMER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((TimerBlockEntity) be).serverTick();
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
                && level.getBlockEntity(pos) instanceof TimerBlockEntity timer) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, p) -> new TimerMenu(id, inventory, timer),
                            Component.translatable(getDescriptionId())),
                    pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
