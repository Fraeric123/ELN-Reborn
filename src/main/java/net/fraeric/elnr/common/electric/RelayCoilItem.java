package net.fraeric.elnr.common.electric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Coil of a relay. {@code voltage} is the rated coil voltage; a voltage of 0 is a burnt-out coil. */
public class RelayCoilItem extends Item {

    /** Rated voltages of the working coils; index + 1 is the {@code coil} blockstate value. */
    public static final int[] VOLTAGES = {12, 24, 50, 250};
    /** Value of the {@code coil} blockstate for a burnt coil. */
    public static final int STATE_BURNT = VOLTAGES.length + 1;

    private final int voltage;

    public RelayCoilItem(int voltage, Properties properties) {
        super(properties);
        this.voltage = voltage;
    }

    public int getVoltage() {
        return voltage;
    }

    public boolean isBurnt() {
        return voltage <= 0;
    }

    /** @return the {@code coil} blockstate value this coil gives: 1..4 for a working coil, 5 for a burnt one. */
    public int stateIndex() {
        if (isBurnt()) {
            return STATE_BURNT;
        }
        for (int i = 0; i < VOLTAGES.length; i++) {
            if (VOLTAGES[i] == voltage) {
                return i + 1;
            }
        }
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isBurnt()) {
            tooltip.add(Component.translatable("tooltip.elnr.relay_coil.burnt").withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.elnr.relay_coil.burnt_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.elnr.relay_coil.voltage", voltage).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.elnr.relay_coil.pull_in", (int) Math.ceil(voltage * RelayBlockEntity.PULL_IN)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.relay_coil.limit", (int) (voltage * 1.3D)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.relay_coil.usage").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
