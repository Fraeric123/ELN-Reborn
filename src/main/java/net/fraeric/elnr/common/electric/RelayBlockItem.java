package net.fraeric.elnr.common.electric;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Item of a relay base; shows its rating and the coil it carries. */
public class RelayBlockItem extends BlockItem {

    public RelayBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (getBlock() instanceof RelayBlock relay) {
            tooltip.add(Component.translatable("tooltip.elnr.relay.rating",
                    (int) relay.getRatedVoltage(), (int) relay.getRatedCurrent()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.elnr.relay.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elnr.relay.2").withStyle(ChatFormatting.DARK_GRAY));
        CompoundTag data = stack.getTagElement("BlockEntityTag");
        if (data != null && data.contains("Coil")) {
            ItemStack coil = ItemStack.of(data.getCompound("Coil"));
            if (!coil.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.elnr.relay.coil", coil.getHoverName()).withStyle(ChatFormatting.YELLOW));
                return;
            }
        }
        tooltip.add(Component.translatable("tooltip.elnr.relay.no_coil").withStyle(ChatFormatting.YELLOW));
    }
}
