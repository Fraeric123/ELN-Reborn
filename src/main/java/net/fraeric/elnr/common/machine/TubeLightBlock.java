package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.common.cable.CableColors;
import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Fluorescent lamp or neon strip: a tube that lies on any surface like the switch (sneak + click
 * with an empty hand turns it). Tubes of the same kind and colour join like cables: in line, in
 * T / cross junctions, around outer corners and into inner corners. Joined tubes are one lamp.
 * The shape is drawn by a block entity renderer from the same boxes that make the hitbox.
 */
public class TubeLightBlock extends MachineBlock {

    public enum Kind { FLUORESCENT, NEON }

    /** Direction the tube points away from the surface it is mounted on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Quarter turns of the tube axis around the mounting normal (same as the switch). */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 15);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final int MAX_GROUP = 128;

    private final Kind kind;
    private final double voltage;
    private final double watts;
    private final int thickness;

    public TubeLightBlock(Properties properties, Kind kind, double voltage, double watts, int thickness) {
        super(properties.noOcclusion().lightLevel(s -> s.getValue(LIT) ? (kind == Kind.NEON ? 12 : 15) : 0));
        this.kind = kind;
        this.voltage = voltage;
        this.watts = watts;
        this.thickness = thickness;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ROTATION, 0)
                .setValue(COLOR, 0).setValue(LIT, false));
    }

    public Kind getKind() { return kind; }
    public double getVoltage() { return voltage; }
    public double getWatts() { return watts; }
    public int getThickness() { return thickness; }

    public static int rgbOf(BlockState state) {
        return CableColors.RGB[state.getValue(COLOR)];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // ---------------- orientation ----------------

    static Direction cross(Direction a, Direction b) {
        int x = a.getStepY() * b.getStepZ() - a.getStepZ() * b.getStepY();
        int y = a.getStepZ() * b.getStepX() - a.getStepX() * b.getStepZ();
        int z = a.getStepX() * b.getStepY() - a.getStepY() * b.getStepX();
        return Direction.getNearest(x, y, z);
    }

    public static Direction terminalDirection(Direction facing, int rotation) {
        Direction t = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        for (int i = 0; i < (rotation & 3); i++) {
            t = cross(facing, t);
        }
        return t;
    }

    public static Direction axisDir(BlockState state) {
        return terminalDirection(state.getValue(FACING), state.getValue(ROTATION));
    }

    // ---------------- connections ----------------

    private record Link(int type, BlockPos pos) {
    }

    private static boolean same(BlockState a, BlockState b) {
        return a.getBlock() == b.getBlock() && a.getValue(COLOR) == b.getValue(COLOR);
    }

    /** The tube that the arm of the tube at {@code p} pointing to {@code d} joins, or null. */
    private static Link find(BlockGetter level, BlockPos p, BlockState st, Direction d) {
        Direction f = st.getValue(FACING);
        // in line (or the lower end of a tube that climbs up a wall towards this one)
        BlockPos n = p.relative(d);
        BlockState ns = level.getBlockState(n);
        if (same(st, ns) && (ns.getValue(FACING) == f || ns.getValue(FACING) == d.getOpposite())) {
            return new Link(TubeLightGeometry.LINE, n);
        }
        // outer corner: the tube goes on over the edge of the support block
        BlockPos c = p.relative(f.getOpposite()).relative(d);
        BlockState cs = level.getBlockState(c);
        if (same(st, cs) && cs.getValue(FACING) == d) {
            return new Link(f.get3DDataValue() < d.get3DDataValue() ? TubeLightGeometry.CONVEX_A : TubeLightGeometry.CONVEX_B, c);
        }
        // inner corner: the tube climbs up the wall and goes on in the block above it
        BlockPos u = p.relative(f);
        BlockState us = level.getBlockState(u);
        if (same(st, us) && us.getValue(FACING) == d.getOpposite()) {
            return new Link(TubeLightGeometry.CONCAVE, u);
        }
        return null;
    }

    /** Arm types (see {@link TubeLightGeometry}) of the tube at {@code p}, in the order of {@code planeDirs}. */
    static int[] computeArms(BlockGetter level, BlockPos p, BlockState st) {
        Direction f = st.getValue(FACING);
        Direction[] dirs = TubeLightGeometry.planeDirs(f);
        int[] arms = new int[4];
        int links = 0;
        int last = -1;
        for (int i = 0; i < 4; i++) {
            Link l = find(level, p, st, dirs[i]);
            if (l != null) {
                arms[i] = l.type();
                links++;
                last = i;
            }
        }
        Direction axis = axisDir(st);
        if (links == 0) {
            arms[TubeLightGeometry.indexOf(dirs, axis)] = TubeLightGeometry.FREE;
            arms[TubeLightGeometry.indexOf(dirs, axis.getOpposite())] = TubeLightGeometry.FREE;
        } else if (links == 1) {
            int o = TubeLightGeometry.indexOf(dirs, dirs[last].getOpposite());
            arms[o] = TubeLightGeometry.FREE;
        }
        return arms;
    }

    static int[] defaultArms(BlockState st) {
        Direction[] dirs = TubeLightGeometry.planeDirs(st.getValue(FACING));
        int[] arms = new int[4];
        Direction axis = axisDir(st);
        arms[TubeLightGeometry.indexOf(dirs, axis)] = TubeLightGeometry.FREE;
        arms[TubeLightGeometry.indexOf(dirs, axis.getOpposite())] = TubeLightGeometry.FREE;
        return arms;
    }

    /** Every tube that forms one lamp with the one at {@code pos}. */
    public static List<BlockPos> group(BlockGetter level, BlockPos pos) {
        BlockState start = level.getBlockState(pos);
        if (!(start.getBlock() instanceof TubeLightBlock)) {
            return List.of(pos);
        }
        Set<BlockPos> seen = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos.immutable());
        queue.add(pos.immutable());
        while (!queue.isEmpty() && seen.size() < MAX_GROUP) {
            BlockPos p = queue.poll();
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof TubeLightBlock)) {
                continue;
            }
            for (Direction d : TubeLightGeometry.planeDirs(st.getValue(FACING))) {
                Link l = find(level, p, st, d);
                if (l == null || seen.contains(l.pos())) {
                    continue;
                }
                BlockState ns = level.getBlockState(l.pos());
                if (!(ns.getBlock() instanceof TubeLightBlock)) {
                    continue;
                }
                // the link has to be mutual
                boolean back = false;
                for (Direction e : TubeLightGeometry.planeDirs(ns.getValue(FACING))) {
                    Link m = find(level, l.pos(), ns, e);
                    if (m != null && m.pos().equals(p)) {
                        back = true;
                        break;
                    }
                }
                if (back) {
                    seen.add(l.pos().immutable());
                    queue.add(l.pos().immutable());
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** Recomputes the shape of every tube around {@code pos} (also diagonally). */
    static void refresh(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof TubeLightBlock && level.getBlockEntity(p) instanceof TubeLightBlockEntity be) {
                        be.setArms(computeArms(level, p, st));
                    }
                }
            }
        }
    }

    // ---------------- block behaviour ----------------

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION, COLOR, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        Direction wanted = facing.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection() : facing.getClockWise();
        int best = 0;
        int bestDot = Integer.MIN_VALUE;
        for (int rotation = 0; rotation < 4; rotation++) {
            Direction t = terminalDirection(facing, rotation);
            int dot = t.getStepX() * wanted.getStepX() + t.getStepY() * wanted.getStepY() + t.getStepZ() * wanted.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = rotation;
            }
        }
        int color = context.getItemInHand().getItem() instanceof TubeLightItem item ? item.getColor() : 0;
        return defaultBlockState().setValue(FACING, facing).setValue(ROTATION, best).setValue(COLOR, color);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
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
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && old.getBlock() != state.getBlock()) {
            refresh(level, pos);
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            refresh(level, pos);
            for (BlockPos n : new BlockPos[]{pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
                ElectricNetwork.updateAt(level, n);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof CableItem) {
            return InteractionResult.PASS;
        }
        // Sneak + empty hand turns the tube around the surface, like the switch.
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, state.cycle(ROTATION));
                refresh(level, pos);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.9F);
                ElectricNetwork.updateAt(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    // ---------------- items ----------------

    private ItemStack stackOf(BlockState state) {
        int color = state.getValue(COLOR);
        for (var reg : ModMachines.TUBE_LIGHT_ITEMS) {
            if (reg.get() instanceof TubeLightItem item && item.getBlock() == this && item.getColor() == color) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(stackOf(state));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return stackOf(state);
    }

    // ---------------- shape ----------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int[] arms = level.getBlockEntity(pos) instanceof TubeLightBlockEntity be && be.getArms() != null
                ? be.getArms() : defaultArms(state);
        VoxelShape shape = Shapes.empty();
        for (TubeLightGeometry.Box b : TubeLightGeometry.boxes(state.getValue(FACING), arms, kind, thickness)) {
            double x0 = Math.max(0, b.x0()), y0 = Math.max(0, b.y0()), z0 = Math.max(0, b.z0());
            double x1 = Math.min(16, b.x1()), y1 = Math.min(16, b.y1()), z1 = Math.min(16, b.z1());
            if (x1 - x0 > 0.001D && y1 - y0 > 0.001D && z1 - z0 > 0.001D) {
                shape = Shapes.or(shape, Shapes.box(x0 / 16.0D, y0 / 16.0D, z0 / 16.0D, x1 / 16.0D, y1 / 16.0D, z1 / 16.0D));
            }
        }
        return shape;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TubeLightBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModMachines.TUBE_LIGHT_BE.get(), TubeLightBlockEntity::serverTick);
    }
}
