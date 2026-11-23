package net.fraeric.elnr.common.cable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Overhead line used only to connect cable stands. Each line belongs to a voltage class and is
 * either a single wire or a bundle of all 16 wires, and only fits stands of the same class and kind.
 */
public class OverheadLineItem extends Item {
    private final CableTier tier;
    private final boolean bundled;

    public OverheadLineItem(CableTier tier, boolean bundled, Properties properties) {
        super(properties);
        this.tier = tier;
        this.bundled = bundled;
    }

    public CableTier getTier() {
        return tier;
    }

    public boolean isBundled() {
        return bundled;
    }

    /** @return true when this line can be hung between stands of the given kind. */
    public boolean fits(StandBlock stand) {
        return stand.getTier() == tier && stand.isBundled() == bundled;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(bundled ? "tooltip.elnr.overhead_line.bundled"
                : "tooltip.elnr.overhead_line.single").withStyle(ChatFormatting.AQUA));
        if (tier.isSignal()) {
            tooltip.add(Component.translatable("tooltip.elnr.signal_stand").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.elnr.cable.voltage", tier.getVoltage()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.cable.current", tier.getMaxCurrent()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.elnr.stand.span", (int) tier.getMaxSpan()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.overhead_line.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
