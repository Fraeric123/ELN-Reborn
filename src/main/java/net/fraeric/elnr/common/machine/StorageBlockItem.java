package net.fraeric.elnr.common.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class StorageBlockItem extends BlockItem {

    public StorageBlockItem(StorageBlock block, Properties properties) {
        super(block, properties);
    }

    private StorageBlock storage() {
        return (StorageBlock) getBlock();
    }

    public static double energyOf(ItemStack stack) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        return tag == null ? 0.0D : tag.getDouble("Energy");
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energyOf(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * (float) (energyOf(stack) / storage().maxEnergy())), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return storage().isBattery() ? 0x55FF55 : 0x55AAFF;
    }

    private static String fmt(double joules) {
        if (joules >= 1.0E6D) {
            return String.format(Locale.ROOT, "%.2f MJ", joules / 1.0E6D);
        }
        if (joules >= 1.0E3D) {
            return String.format(Locale.ROOT, "%.1f kJ", joules / 1.0E3D);
        }
        return String.format(Locale.ROOT, "%.0f J", joules);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        StorageBlock b = storage();
        String cap = b.isBattery() ? (int) b.getCapacity() + " Ah"
                : (b.getCapacity() < 1 ? (int) (b.getCapacity() * 1000) + " mF" : (int) b.getCapacity() + " F");
        tooltip.add(Component.translatable("tooltip.elnr.storage.rating", (int) b.getVoltage(), cap).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.storage.energy", fmt(energyOf(stack)), fmt(b.maxEnergy()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(b.isBattery() ? "tooltip.elnr.battery" : "tooltip.elnr.capacitor")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
