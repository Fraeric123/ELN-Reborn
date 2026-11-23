package net.fraeric.elnr.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Incandescent bulb with a rated voltage (12 / 50 / 250 V, 50 W) and an optional glass colour. */
public class BulbItem extends Item {

    public static final double POWER = 50.0D;
    public static final int DEFAULT_VOLTAGE = 50;

    private final int voltage;
    /** Glass tint as RGB, or -1 for clear glass. */
    private final int tint;
    @Nullable
    private final String colorKey;

    public BulbItem(Properties properties, int voltage, int tint, @Nullable String colorKey) {
        super(properties);
        this.voltage = voltage;
        this.tint = tint;
        this.colorKey = colorKey;
    }

    public int getVoltage() {
        return voltage;
    }

    public double getResistance() {
        return voltage * (double) voltage / POWER;
    }

    /** Tint used for the glass in the socket model (white = clear). */
    public int getTint() {
        return tint < 0 ? 0xFFFFFF : tint;
    }

    public static int voltageOf(ItemStack stack) {
        return stack.getItem() instanceof BulbItem bulb ? bulb.voltage : DEFAULT_VOLTAGE;
    }

    public static double resistanceOf(ItemStack stack) {
        return stack.getItem() instanceof BulbItem bulb ? bulb.getResistance() : 50.0D;
    }

    public static int tintOf(ItemStack stack) {
        if (stack.getItem() instanceof BrokenBulbItem broken) {
            return broken.getTint();
        }
        return stack.getItem() instanceof BulbItem bulb ? bulb.getTint() : 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.elnr.bulb.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.bulb.rating", voltage,
                String.format(java.util.Locale.ROOT, "%.4g", getResistance()), (int) POWER).withStyle(ChatFormatting.GRAY));
        if (colorKey != null) {
            tooltip.add(Component.translatable("color.elnr." + colorKey).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
