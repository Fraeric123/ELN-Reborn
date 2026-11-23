package net.fraeric.elnr.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What stays in the socket after a bulb fails: a burnt-out bulb (overvoltage) or a cracked one (heavy overvoltage). */
public class BrokenBulbItem extends Item {

    private final boolean cracked;

    public BrokenBulbItem(Properties properties, boolean cracked) {
        super(properties);
        this.cracked = cracked;
    }

    public boolean isCracked() {
        return cracked;
    }

    /** Glass tint in the socket model: dark for burnt, pale for cracked. */
    public int getTint() {
        return cracked ? 0xB4BCC4 : 0x3A3836;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(cracked ? "tooltip.elnr.cracked_bulb" : "tooltip.elnr.burnt_bulb")
                .withStyle(ChatFormatting.GRAY));
    }
}
