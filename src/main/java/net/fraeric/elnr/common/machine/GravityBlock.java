package net.fraeric.elnr.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Artificial gravity generator. FACING is the side the gravity column comes out of.
 * Touching generators form one device (one cable is enough, the settings are shared).
 */
public class GravityBlock extends MachineBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final int MAX_GROUP = 64;

    public GravityBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The column comes out of the clicked side (away from the block it is placed on); sneaking reverses it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        boolean sneak = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        return defaultBlockState().setValue(FACING, sneak ? face.getOpposite() : face);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravityBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GravityBlockEntity be) {
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(pos.relative(d)) instanceof GravityBlockEntity other) {
                    be.copyConfig(other);
                    break;
                }
            }
        }
    }

    /** All generators touching {@code pos} (face to face), including {@code pos}. */
    public static List<BlockPos> group(BlockGetter level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockState(pos).getBlock() instanceof GravityBlock)) {
            out.add(pos);
            return out;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty() && out.size() < MAX_GROUP) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof GravityBlock) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return out;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (isCable(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof GravityBlockEntity be) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new GravityMenu(id, inv, be),
                    Component.translatable("block.elnr.gravity_generator")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
