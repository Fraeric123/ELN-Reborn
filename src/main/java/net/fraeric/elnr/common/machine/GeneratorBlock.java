package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Wood burning generator with a selectable output voltage. */
public class GeneratorBlock extends MachineBlock {

    public GeneratorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (isCable(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof GeneratorBlockEntity be) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new GeneratorMenu(id, inv, be),
                    Component.translatable("block.elnr.generator")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
