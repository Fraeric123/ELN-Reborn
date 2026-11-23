package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stackable leg for solar panels: stack it up to raise the panel higher; it is part of the panel on top of it. */
public class SolarPanelLegBlock extends Block implements ElectricMachineBlock {

    /** True when there is no leg below (draws the foot plate). */
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    /** True when there is no leg above (draws the bracket the panel sits on). */
    public static final BooleanProperty CAP = BooleanProperty.create("cap");

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public SolarPanelLegBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(BASE, true).setValue(CAP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE, CAP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        return defaultBlockState()
                .setValue(BASE, !ctx.getLevel().getBlockState(pos.below()).is(this))
                .setValue(CAP, !ctx.getLevel().getBlockState(pos.above()).is(this));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN) return state.setValue(BASE, !neighbor.is(this));
        if (dir == Direction.UP) return state.setValue(CAP, !neighbor.is(this));
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Lowest position of the leg column containing {@code pos}. */
    private static BlockPos bottomOf(BlockGetter level, BlockPos pos) {
        BlockPos p = pos;
        while (level.getBlockState(p.below()).getBlock() instanceof SolarPanelLegBlock) p = p.below();
        return p;
    }

    /** Position of the panel tile standing on the leg column of {@code pos}, or null. */
    public static BlockPos panelOf(BlockGetter level, BlockPos pos) {
        BlockPos p = pos;
        while (level.getBlockState(p).getBlock() instanceof SolarPanelLegBlock) p = p.above();
        return level.getBlockState(p).getBlock() instanceof SolarPanelBlock ? p : null;
    }

    /** Leg columns + the whole connected panel above them behave as one device (also seen from a panel block). */
    public static List<BlockPos> group(BlockGetter level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos seed = pos;
        if (level.getBlockState(pos).getBlock() instanceof SolarPanelLegBlock) {
            BlockPos p = bottomOf(level, pos);
            while (level.getBlockState(p).getBlock() instanceof SolarPanelLegBlock) {
                out.add(p);
                p = p.above();
            }
            seed = level.getBlockState(p).getBlock() instanceof SolarPanelBlock ? p : null;
        }
        if (seed != null) {
            for (BlockPos tile : SolarPanelBlock.connected(level, seed)) {
                if (!out.contains(tile)) out.add(tile);
                // legs standing under the panel blocks
                BlockPos p = tile.below();
                if (level.getBlockState(p).getBlock() instanceof SolarPanelLegBlock) {
                    p = bottomOf(level, p);
                    while (level.getBlockState(p).getBlock() instanceof SolarPanelLegBlock) {
                        if (!out.contains(p)) out.add(p);
                        p = p.above();
                    }
                }
            }
        }
        if (out.isEmpty()) out.add(pos);
        return out;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof net.fraeric.elnr.common.cable.CableItem) {
            return InteractionResult.PASS;
        }
        BlockPos panel = panelOf(level, pos);
        if (panel == null) return InteractionResult.PASS;
        if (!level.isClientSide) SolarPanelBlock.showInfo(level, panel, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && old.getBlock() != state.getBlock()) ElectricNetwork.updateAt(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) ElectricNetwork.updateAt(level, pos);
    }
}
