package net.fraeric.elnr.common.wireless;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Wireless remote: click sends a pulse on its frequency, sneak + click opens the frequency screen. */
public class RemoteItem extends Item {

    public RemoteItem(Properties properties) {
        super(properties);
    }

    public static int getFrequency(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, Math.min(FrequencyHolder.MAX_FREQUENCY, stack.getTag().getInt("Frequency"))) : 0;
    }

    public static void setFrequency(ItemStack stack, int frequency) {
        stack.getOrCreateTag().putInt("Frequency", frequency);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider((id, inv, p) -> new RemoteMenu(id, inv, hand),
                                Component.translatable("item.elnr.wireless_remote")),
                        buf -> buf.writeEnum(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            int frequency = getFrequency(stack);
            int reached = WirelessManager.pulse(serverLevel, player.getEyePosition(), frequency);
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.4F, 1.4F);
            player.displayClientMessage(Component.translatable("msg.elnr.remote_sent", frequency, reached), true);
            player.getCooldowns().addCooldown(this, 8);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.elnr.remote.frequency", getFrequency(stack)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.elnr.remote.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.remote.2").withStyle(ChatFormatting.GRAY));
    }
}
