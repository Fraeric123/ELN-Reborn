package net.fraeric.elnr.common.probe;

import net.fraeric.elnr.common.item.TooltipItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Locale;
import java.util.Optional;

/**
 * Multimeter or thermometer. Right-click a cable or device to print the reading into
 * the chat of the player who clicked (nobody else sees it).
 */
public class ProbeItem extends TooltipItem {

    public enum Mode {
        MULTIMETER, THERMOMETER
    }

    private final Mode mode;

    public ProbeItem(Properties properties, Mode mode, String... tooltipKeys) {
        super(properties, tooltipKeys);
        this.mode = mode;
    }

    /**
     * Runs before the block's own right-click handler, so measuring a voltage source or a
     * switch does not also change it.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        Optional<ElectricProbe.Reading> reading = ElectricProbe.measure(
                context.getLevel(), context.getClickedPos(), context.getClickLocation());
        if (reading.isEmpty()) {
            // Not a cable or device: let the click act normally.
            return InteractionResult.PASS;
        }
        ElectricProbe.Reading r = reading.get();
        if (mode == Mode.MULTIMETER) {
            player.sendSystemMessage(Component.translatable("message.elnr.multimeter.header", r.name())
                    .withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.elnr.multimeter.voltage", format(r.voltage()))
                    .withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.translatable("message.elnr.multimeter.current", format(r.current()))
                    .withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.translatable("message.elnr.multimeter.power", format(r.power()))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.translatable("message.elnr.thermometer.reading",
                    r.name(), format(r.temperature())).withStyle(ChatFormatting.YELLOW));
        }
        return InteractionResult.SUCCESS;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
