package net.fraeric.elnr.common.item;

import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.electric.SocketBlock;
import net.fraeric.elnr.common.electric.SocketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bulb socket item. Besides the normal placement it can be put straight onto a cable strip
 * (not a bundle): the socket replaces the strip and takes over its cable as the plugged
 * cord, so the line simply continues through the socket.
 */
public class SocketItem extends TooltipBlockItem {

    public SocketItem(Block block, Properties properties, String... tooltipKeys) {
        super(block, properties, tooltipKeys);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);
        if (!(clicked.getBlock() instanceof CableBlock cable) || cable.isBundled()) {
            return super.useOn(context);
        }

        Player player = context.getPlayer();
        Direction face = CableBlock.nearestFace(clicked, context.getClickLocation(), pos);
        if (face == null) {
            return InteractionResult.FAIL;
        }
        // The socket takes the place of the whole block, so it can only replace a block that
        // holds a single piece; otherwise the other pieces would be lost.
        if (CableBlock.countFaces(clicked) != 1) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.elnr.socket.single_piece"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockState socketState = getBlock().defaultBlockState().setValue(SocketBlock.FACING, face.getOpposite());
        if (!socketState.canSurvive(level, pos)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            if (!level.setBlock(pos, socketState, Block.UPDATE_ALL)) {
                return InteractionResult.FAIL;
            }
            if (level.getBlockEntity(pos) instanceof SocketBlockEntity socket) {
                socket.setCable(new ItemStack(cable.asItem()));
            }
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
            ElectricNetwork.updateAt(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
