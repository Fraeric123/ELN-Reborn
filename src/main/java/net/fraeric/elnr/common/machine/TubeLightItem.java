package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.common.cable.CableColors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** One colour of a tube light; the colour is stored in the placed block's state. */
public class TubeLightItem extends BlockItem {

    private final TubeLightBlock tube;
    private final int color;

    public TubeLightItem(TubeLightBlock block, int color, Properties properties) {
        super(block, properties);
        this.tube = block;
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public int getRgb() {
        return CableColors.RGB[color];
    }

    @Override
    public Component getName(ItemStack stack) {
        Component thick = Component.translatable("tooltip.elnr.tube.thickness." + tube.getThickness());
        Component col = Component.translatable("color.elnr." + CableColors.NAMES[color]);
        return tube.getKind() == TubeLightBlock.Kind.FLUORESCENT
                ? Component.translatable("block.elnr.tube.fluorescent", thick, col)
                : Component.translatable("block.elnr.tube.neon", (int) tube.getVoltage(), thick, col);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(tube.getKind() == TubeLightBlock.Kind.FLUORESCENT
                ? "tooltip.elnr.tube.fluorescent" : "tooltip.elnr.tube.neon").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.tube.rating", (int) tube.getVoltage(),
                String.format(Locale.ROOT, "%.0f", tube.getWatts())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.tube.join").withStyle(ChatFormatting.DARK_GRAY));
    }
}
