package net.fraeric.elnr.common.cable;

import net.fraeric.elnr.common.electric.BulbHolder;
import net.fraeric.elnr.common.electric.SocketBlock;
import net.fraeric.elnr.common.electric.StreetLampBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cable item that does not place a normal block, but adds a cable piece
 * to the clicked surface (possibly into an already existing cable block).
 */
public class CableItem extends BlockItem {

    public CableItem(CableBlock block, Properties properties) {
        super(block, properties);
    }

    /** @return true when the item is a signal cable (never plugs into sockets or lamps). */
    public boolean isSignal() {
        return getBlock() instanceof CableBlock cable && cable.getTier().isSignal();
    }

    /** @return true when the stack is a power cable, i.e. a cable that can be plugged into a socket or lamp. */
    public static boolean isPowerCable(ItemStack stack) {
        return stack.getItem() instanceof CableItem item && !item.isSignal();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(getBlock() instanceof CableBlock cableBlock)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        // Sneak + right-click on a socket or street lamp plugs the cable into it.
        // (Vanilla skips Block#use while sneaking with an item, so it has to be done
        // here; a plain right-click is handled by the blocks themselves.)
        if (player != null && player.isShiftKeyDown()) {
            BlockState clicked = level.getBlockState(context.getClickedPos());
            if (isSignal() && (clicked.getBlock() instanceof SocketBlock || clicked.getBlock() instanceof StreetLampBlock)) {
                return InteractionResult.FAIL;
            }
            if (clicked.getBlock() instanceof SocketBlock || clicked.getBlock() instanceof StreetLampBlock) {
                BlockPos plugPos = clicked.getBlock() instanceof StreetLampBlock
                        ? StreetLampBlock.basePos(context.getClickedPos(), clicked)
                        : context.getClickedPos();
                if (level.getBlockEntity(plugPos) instanceof BulbHolder holder && !holder.hasCable()) {
                    if (!level.isClientSide) {
                        holder.setCable(new ItemStack(this));
                        if (!player.getAbilities().instabuild) {
                            context.getItemInHand().shrink(1);
                        }
                        ElectricNetwork.updateAt(level, plugPos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                return InteractionResult.FAIL;
            }
        }

        Vec3 look = player != null
                ? player.getLookAngle()
                : Vec3.atLowerCornerOf(context.getHorizontalDirection().getNormal());
        InteractionResult result = CableBlock.tryPlace(level, context.getClickedPos(),
                context.getClickedFace(), context.getClickLocation(), look, cableBlock);
        if (result.consumesAction() && !level.isClientSide) {
            if (player != null && !player.getAbilities().instabuild) {
                ItemStack stack = context.getItemInHand();
                stack.shrink(1);
            }
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (getBlock() instanceof CableBlock cableBlock) {
            CableTier tier = cableBlock.getTier();
            if (tier.isSignal()) {
                tooltip.add(Component.translatable(cableBlock.isBundled() ? "tooltip.elnr.signal_cable.bundled"
                        : cableBlock.getColor() >= 0 ? "tooltip.elnr.signal_cable.insulated"
                        : "tooltip.elnr.signal_cable.bare").withStyle(ChatFormatting.AQUA));
                if (!cableBlock.isBundled() && cableBlock.getColor() >= 0) {
                    tooltip.add(Component.translatable("tooltip.elnr.signal_cable.colour",
                            Component.translatable("color.elnr." + CableColors.NAMES[cableBlock.getColor()])).withStyle(ChatFormatting.GRAY));
                }
                tooltip.add(Component.translatable("tooltip.elnr.signal_cable.1").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.elnr.signal_cable.2").withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("tooltip.elnr.cable.remove").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            if (cableBlock.isBundled()) {
                tooltip.add(Component.translatable("tooltip.elnr.cable.bundled").withStyle(ChatFormatting.AQUA));
            } else if (cableBlock.getColor() >= 0) {
                tooltip.add(Component.translatable("tooltip.elnr.cable.insulated",
                        Component.translatable("color.elnr." + CableColors.NAMES[cableBlock.getColor()])).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable("tooltip.elnr.cable.voltage", tier.getVoltage()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.cable.current", tier.getMaxCurrent()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.cable.power", tier.getMaxPower()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.cable.usage").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.elnr.cable.remove").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
