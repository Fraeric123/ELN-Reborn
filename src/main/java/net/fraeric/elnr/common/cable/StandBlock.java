package net.fraeric.elnr.common.cable;

import net.fraeric.elnr.common.electric.DeviceShapes;
import net.fraeric.elnr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cable stand (a post with an insulator) that carries a cable through the air.
 *
 * <p>A stand is made for one cable type: a voltage tier and either a single wire (bare or one of
 * the 16 insulation colours) or a bundle of all 16 wires, exactly like the cable blocks. It can
 * be mounted on the floor, on walls and on the ceiling ({@link #FACING} points away from the
 * support), and stands can be stacked on their open end to build a column.</p>
 *
 * <p>Two stands of the same type are joined with an overhead line: right-click the first stand
 * with the matching overhead line, then the second one. The line costs one overhead line and is limited in length
 * by the tier. Sneak + empty hand removes the newest line of a stand again.</p>
 *
 * <p>Electrically a stand is a hub, like a socket: it joins every compatible cable piece next to
 * it, the stands directly above and below it in its column and the stands at the other end of
 * its overhead lines. See {@link ElectricNetwork}.</p>
 */
public class StandBlock extends Block implements EntityBlock {

    /** Direction the stand points away from the surface it is mounted on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Height (1/16 block, floor frame) at which the overhead lines are attached. */
    private static final double ANCHOR_HEIGHT = 11.5D;

    /** First stand of a line a player has selected, per player. */
    private record Pending(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private final CableTier tier;
    private final int color;
    private final boolean bundled;
    private CableBlock cableType;

    public StandBlock(CableTier tier, int color, boolean bundled, Properties properties) {
        super(properties);
        this.tier = tier;
        this.color = color;
        this.bundled = bundled;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public CableTier getTier() {
        return tier;
    }

    public int getColor() {
        return color;
    }

    public boolean isBundled() {
        return bundled;
    }

    /** @return the cable block this stand stands in for (same tier, colour and bundling). */
    public CableBlock cableType() {
        if (cableType == null) {
            cableType = ModBlocks.findCable(tier, color, bundled);
        }
        return cableType;
    }

    /** The overhead line item that was used to hang a line on this stand (what cutting the line gives back). */
    public ItemStack overheadLineStack() {
        for (var reg : net.fraeric.elnr.registry.ModItems.OVERHEAD_LINES) {
            if (reg.get() instanceof OverheadLineItem line && line.getTier() == tier && line.isBundled() == bundled) {
                return new ItemStack(line);
            }
        }
        return new ItemStack(cableType().asItem());
    }

    /** @return the longest overhead line this stand can span, in blocks. */
    public double getMaxSpan() {
        return tier.getMaxSpan();
    }

    /** Width of the post in 1/16 block; must match {@code stand_*.json}. */
    public double postWidth() {
        return switch (tier) {
            case LV -> 2.0D;
            case MV -> 3.0D;
            case HV -> 4.0D;
            case VHV -> 5.0D;
            case SIGNAL -> 1.0D;
        };
    }

    /** Number of insulator discs of the model. */
    private int discCount() {
        return switch (tier) {
            case LV, SIGNAL -> 1;
            case MV -> 2;
            default -> 3;
        };
    }

    /** How far a cable strip lying on the same surface runs into the stand, in blocks. */
    public float getReach() {
        return (float) ((16.0D - postWidth()) / 2.0D / 16.0D);
    }

    /** RGB tint of the cable clamp (model tint index 0). */
    public int getTintRgb() {
        if (bundled) {
            return 0x6B4F2A;
        }
        return color >= 0 ? CableColors.RGB[color] : 0x404040;
    }

    /** @return the attachment point of the overhead lines, in block-local coordinates. */
    public static Vec3 anchor(BlockState state) {
        if (state.getBlock() instanceof StandBlock) {
            return DeviceShapes.point(state.getValue(FACING), 8.0D, ANCHOR_HEIGHT, 8.0D);
        }
        return new Vec3(0.5D, 0.5D, 0.5D);
    }

    // ------------------------------------------------------------------
    // Network
    // ------------------------------------------------------------------

    private static boolean loaded(BlockGetter level, BlockPos pos) {
        return !(level instanceof LevelReader reader) || reader.hasChunkAt(pos);
    }

    /**
     * @return the pieces electrically joined to the given stand node: cable pieces touching the
     * stand, the stands above and below it and the stands at the far end of its overhead lines
     */
    public static List<ElectricNetwork.Node> getConnections(BlockGetter level, ElectricNetwork.Node node) {
        List<ElectricNetwork.Node> out = new ArrayList<>();
        BlockPos pos = node.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StandBlock stand)) {
            return out;
        }
        CableBlock self = stand.cableType();
        int channel = node.channel();
        Direction facing = state.getValue(FACING);

        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            if (!loaded(level, neighbourPos)) {
                continue;
            }
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (neighbour.getBlock() instanceof CableBlock other && CableBlock.canLink(self, channel, other)) {
                int otherChannel = CableBlock.linkChannel(self, channel, other);
                for (Direction face : Direction.values()) {
                    if (other.hasFace(neighbour, face)) {
                        out.add(new ElectricNetwork.Node(neighbourPos.immutable(), face, otherChannel));
                    }
                }
            }
        }

        // The column: stands stacked on both ends along the facing axis.
        for (Direction direction : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos neighbourPos = pos.relative(direction);
            if (!loaded(level, neighbourPos)) {
                continue;
            }
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (neighbour.getBlock() instanceof StandBlock other && neighbour.getValue(FACING) == facing
                    && CableBlock.canLink(self, channel, other.cableType())) {
                out.add(new ElectricNetwork.Node(neighbourPos.immutable(), Direction.DOWN,
                        CableBlock.linkChannel(self, channel, other.cableType())));
            }
        }

        // Overhead lines.
        if (level.getBlockEntity(pos) instanceof StandBlockEntity entity) {
            for (BlockPos link : entity.getLinks()) {
                if (!loaded(level, link)) {
                    continue;
                }
                BlockState other = level.getBlockState(link);
                if (other.getBlock() instanceof StandBlock otherStand
                        && CableBlock.canLink(self, channel, otherStand.cableType())) {
                    out.add(new ElectricNetwork.Node(link.immutable(), Direction.DOWN,
                            CableBlock.linkChannel(self, channel, otherStand.cableType())));
                }
            }
        }
        return out;
    }

    /** @return the channels a node of this stand occupies: one (-1) or the 16 wires of a bundle. */
    public int[] channels() {
        return cableType().channels();
    }

    // ------------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StandBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Clicking the open end of a stand continues its column, because the clicked face
        // is then the stand's own facing.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        if (support.getBlock() instanceof StandBlock && support.getValue(FACING) == facing) {
            return true;
        }
        return support.isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double h = postWidth() / 2.0D;
        double[] post = {8 - h, 0, 8 - h, 8 + h, 16, 8 + h};
        double[] flange = {8 - h - 1, 0.5, 8 - h - 1, 8 + h + 1, 1.5, 8 + h + 1};
        // Insulator discs (1.5 wider than the post on every side) and the clamp ring above them.
        double[] collar = {8 - h - 1.5, 10.9 - discCount(), 8 - h - 1.5, 8 + h + 1.5, 12, 8 + h + 1.5};
        return Shapes.or(DeviceShapes.device(state.getValue(FACING), post),
                DeviceShapes.device(state.getValue(FACING), flange),
                DeviceShapes.device(state.getValue(FACING), collar));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(asItem()));
    }

    // ------------------------------------------------------------------
    // Overhead lines
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);

        // Sneak + empty hand removes the newest line.
        if (held.isEmpty() && player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!(level.getBlockEntity(pos) instanceof StandBlockEntity entity) || entity.getLinks().isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                List<BlockPos> links = entity.getLinks();
                BlockPos other = links.get(links.size() - 1);
                entity.removeLink(other);
                if (level.hasChunkAt(other) && level.getBlockEntity(other) instanceof StandBlockEntity partner) {
                    partner.removeLink(pos);
                }
                popResource(level, pos, overheadLineStack());
                level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.5F, 1.0F);
                player.displayClientMessage(Component.translatable("message.elnr.stand.removed"), true);
                ElectricNetwork.updateAt(level, pos);
                ElectricNetwork.updateAt(level, other);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Only the cable this stand is made for builds a line; any other cable is laid as usual.
        if (!(held.getItem() instanceof OverheadLineItem line)) {
            return InteractionResult.PASS;
        }
        if (!line.fits(this)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.elnr.stand.wrong_line"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.dimension().equals(level.dimension())) {
            select(player, level, pos);
            return InteractionResult.CONSUME;
        }
        if (pending.pos().equals(pos)) {
            PENDING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.elnr.stand.cancelled"), true);
            return InteractionResult.CONSUME;
        }

        BlockState otherState = level.getBlockState(pending.pos());
        if (!level.hasChunkAt(pending.pos()) || otherState.getBlock() != this
                || !(level.getBlockEntity(pending.pos()) instanceof StandBlockEntity first)
                || !(level.getBlockEntity(pos) instanceof StandBlockEntity second)) {
            player.displayClientMessage(Component.translatable("message.elnr.stand.lost"), true);
            select(player, level, pos);
            return InteractionResult.CONSUME;
        }
        if (first.hasLink(pos)) {
            player.displayClientMessage(Component.translatable("message.elnr.stand.exists"), true);
            PENDING.remove(player.getUUID());
            return InteractionResult.CONSUME;
        }
        double distance = Vec3.atLowerCornerOf(pending.pos()).add(anchor(otherState))
                .distanceTo(Vec3.atLowerCornerOf(pos).add(anchor(state)));
        if (distance > getMaxSpan()) {
            player.displayClientMessage(Component.translatable("message.elnr.stand.too_far",
                    (int) getMaxSpan()), true);
            return InteractionResult.CONSUME;
        }

        first.addLink(pos);
        second.addLink(pending.pos());
        PENDING.remove(player.getUUID());
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
        player.displayClientMessage(Component.translatable("message.elnr.stand.linked"), true);
        ElectricNetwork.updateAt(level, pos);
        ElectricNetwork.updateAt(level, pending.pos());
        return InteractionResult.CONSUME;
    }

    private static void select(Player player, Level level, BlockPos pos) {
        PENDING.put(player.getUUID(), new Pending(level.dimension(), pos.immutable()));
        player.displayClientMessage(Component.translatable("message.elnr.stand.selected"), true);
    }

    // ------------------------------------------------------------------
    // Block behaviour
    // ------------------------------------------------------------------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof StandBlockEntity entity) {
            // Cut every overhead line; each one gives its cable back.
            List<BlockPos> others = new ArrayList<>(entity.getLinks());
            for (BlockPos other : others) {
                if (level.hasChunkAt(other) && level.getBlockEntity(other) instanceof StandBlockEntity partner) {
                    partner.removeLink(pos);
                }
                popResource(level, pos, overheadLineStack());
            }
            super.onRemove(state, level, pos, newState, isMoving);
            for (BlockPos other : others) {
                if (level.hasChunkAt(other)) {
                    ElectricNetwork.updateAt(level, other);
                }
            }
            ElectricNetwork.updateAt(level, pos);
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }
}
