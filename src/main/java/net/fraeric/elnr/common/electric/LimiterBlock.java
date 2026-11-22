package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Voltage / current limiter: a two terminal switch that closes only while the measured voltage
 * and current are inside configurable windows. Right-click opens the settings, sneak + empty
 * hand turns it like the other switches.
 */
public class LimiterBlock extends SwitchBlock implements EntityBlock {

    private static final double[] HOUSING = {4, 0, 4, 12, 3, 12};
    private static final double[] LED = {6, 3, 6, 10, 3.5, 10};
    private static final double[] PIN_A = {7, 0, 0, 9, 1, 4};
    private static final double[] PIN_B = {7, 0, 12, 9, 1, 16};

    public LimiterBlock(Properties properties, double ratedVoltage, double ratedCurrent) {
        super(properties, ratedVoltage, ratedCurrent);
    }

    @Override
    protected double[][] boxes(BlockState state) {
        return new double[][]{HOUSING, LED, PIN_A, PIN_B};
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LimiterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.LIMITER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((LimiterBlockEntity) be).serverTick();
    }

    /** Driven by the measurement, not by clicking. */
    @Override
    protected void activate(BlockState state, Level level, BlockPos pos) {
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
                && level.getBlockEntity(pos) instanceof LimiterBlockEntity limiter) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, p) -> new LimiterMenu(id, inventory, limiter),
                            Component.translatable(getDescriptionId())),
                    pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
