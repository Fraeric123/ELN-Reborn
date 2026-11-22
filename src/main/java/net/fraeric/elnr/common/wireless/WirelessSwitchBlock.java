package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.fraeric.elnr.registry.ModWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Switch closed by wireless signals: a remote pulse toggles it, a powered transmitter on
 * the same frequency in range holds it closed. Right click opens the frequency screen,
 * sneak + empty hand turns it like the other switches.
 */
public class WirelessSwitchBlock extends SwitchBlock implements EntityBlock {

    public WirelessSwitchBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessSwitchBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof CableItem || held.getItem() instanceof RemoteItem) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof WirelessSwitchBlockEntity be) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new FrequencyMenu(id, inv, be),
                    Component.translatable("block.elnr.wireless_switch")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
