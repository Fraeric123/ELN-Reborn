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

    /**
     * Right-clicking a cable with a stand puts the stand in place of the cable (the cable ends up inside the
     * stand). The stand must be of the same voltage tier and bundled exactly when the cable is. Anything else
     * is placed next to the clicked block as usual, where the stand joins the neighbouring cables and devices
     * by itself.
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        net.minecraft.core.BlockPos pos = context.getClickedPos();
        net.minecraft.world.level.block.state.BlockState clicked = level.getBlockState(pos);
        if (!(clicked.getBlock() instanceof CableBlock cable) || !(getBlock() instanceof StandBlock stand)) {
            return super.useOn(context);
        }
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        if (cable.getTier() != stand.getTier() || cable.isBundled() != stand.isBundled()) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.elnr.stand.mismatch"), true);
            }
            return net.minecraft.world.InteractionResult.FAIL;
        }
        // Only a block with a single cable can be taken over, otherwise the other cables would vanish.
        net.minecraft.core.Direction support = null;
        int faces = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (cable.hasFace(clicked, d)) {
                faces++;
                if (support == null || d == context.getClickedFace()) {
                    support = d;
                }
            }
        }
        if (faces != 1) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        net.minecraft.world.level.block.state.BlockState placed = stand.defaultBlockState()
                .setValue(StandBlock.FACING, support.getOpposite());
        if (!placed.canSurvive(level, pos)) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, placed, net.minecraft.world.level.block.Block.UPDATE_ALL);
            level.playSound(null, pos, placed.getSoundType().getPlaceSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            ElectricNetwork.updateAt(level, pos);
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
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
