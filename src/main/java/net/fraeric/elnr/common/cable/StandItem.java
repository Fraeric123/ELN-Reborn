package net.fraeric.elnr.common.cable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Block item of a cable stand with a tooltip listing its ratings. */
public class StandItem extends BlockItem {

    public StandItem(StandBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!(getBlock() instanceof StandBlock stand)) {
            return;
        }
        CableTier tier = stand.getTier();
        if (stand.isBundled()) {
            tooltip.add(Component.translatable("tooltip.elnr.stand.bundled").withStyle(ChatFormatting.AQUA));
        } else if (stand.getColor() >= 0) {
            tooltip.add(Component.translatable("tooltip.elnr.stand.insulated",
                    Component.translatable("color.elnr." + CableColors.NAMES[stand.getColor()])).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.elnr.stand.bare").withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.elnr.cable.voltage", tier.getVoltage()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.cable.current", tier.getMaxCurrent()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.stand.span", (int) stand.getMaxSpan()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.stand.usage.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.stand.usage.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.stand.usage.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
