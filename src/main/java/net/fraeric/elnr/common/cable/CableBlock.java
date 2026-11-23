package net.fraeric.elnr.common.cable;

import net.fraeric.elnr.common.electric.BulbHolder;
import net.fraeric.elnr.common.electric.SocketBlock;
import net.fraeric.elnr.common.electric.StreetLampBlock;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Surface mounted cable.
 *
 * <p>Every block can contain up to six cable pieces, one for each inner face
 * ({@code down}, {@code up}, {@code north}, {@code south}, {@code west}, {@code east}).
 * Pieces on neighbouring faces of the same block bend around the edge, pieces on the
 * same face in neighbouring blocks join into a straight run.</p>
 */
public class CableBlock extends Block implements EntityBlock {

    public static final Map<Direction, BooleanProperty> FACE_PROPERTIES;

    static {
        EnumMap<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            map.put(direction, BooleanProperty.create(direction.getName()));
        }
        FACE_PROPERTIES = Collections.unmodifiableMap(map);
    }

    private final CableTier tier;
    /** Insulation colour index (0-15), or -1 for the bare cable. */
    private final int color;
    /** Bundled cable: carries all 16 coloured wires at once. */
    private final boolean bundled;

    public CableBlock(CableTier tier, Properties properties) {
        this(tier, -1, false, properties);
    }

    public CableBlock(CableTier tier, int color, boolean bundled, Properties properties) {
        super(properties);
        this.tier = tier;
        this.color = color;
        this.bundled = bundled;

        // BooleanProperty lists TRUE before FALSE, so the raw "any" state has every
        // face enabled. Always register an explicitly empty block instead.
        BlockState empty = defaultBlockState();
        for (Direction direction : Direction.values()) {
            empty = empty.setValue(property(direction), Boolean.FALSE);
        }
        registerDefaultState(empty);
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

    public boolean isBare() {
        return color < 0 && !bundled;
    }

    /** @return the channels a piece of this block occupies: one (-1) or the 16 wires of a bundle. */
    public int[] channels() {
        if (!bundled) {
            return new int[]{-1};
        }
        int[] all = new int[CableColors.COUNT];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    /**
     * Whether a piece of {@code a} (on wire {@code channel} when it is a bundle, -1 = any)
     * is wired to an adjacent piece of {@code b}. Same tier is required. Bare joins bare
     * and every insulated colour, an insulated colour joins its own colour and the same
     * wire of a bundle, different colours never join, a bundle joins bundles and
     * coloured cables only.
     */
    public static boolean canLink(CableBlock a, int channel, CableBlock b) {
        if (a.tier != b.tier) {
            return false;
        }
        if (a.bundled && b.bundled) {
            return true;
        }
        if (a.bundled) {
            return b.color >= 0 && (channel < 0 || channel == b.color);
        }
        if (b.bundled) {
            return a.color >= 0;
        }
        return a.color < 0 || b.color < 0 || a.color == b.color;
    }

    /** Channel of the neighbouring piece {@code b} that {@code a} (on {@code channel}) is wired to. */
    public static int linkChannel(CableBlock a, int channel, CableBlock b) {
        if (!b.bundled) {
            return -1;
        }
        return a.bundled ? channel : a.color;
    }

    public static BooleanProperty property(Direction face) {
        return FACE_PROPERTIES.get(face);
    }

    public boolean hasFace(BlockState state, Direction face) {
        return state.getValue(property(face));
    }

    public BlockState withFace(BlockState state, Direction face) {
        return state.setValue(property(face), Boolean.TRUE);
    }

    public BlockState withoutFace(BlockState state, Direction face) {
        return state.setValue(property(face), Boolean.FALSE);
    }

    public static int countFaces(BlockState state) {
        int count = 0;
        for (BooleanProperty property : FACE_PROPERTIES.values()) {
            if (state.getValue(property)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Connections
    // ------------------------------------------------------------------

    /** How an arm of a cable piece reaches its partner. */
    public enum ArmKind {
        /** Bend inside the same block, onto the neighbouring face (inner corner). */
        INNER,
        /** Straight on, same face of the neighbouring block. */
        STRAIGHT,
        /** Around the outer edge of the supporting block, onto the face of the diagonal block. */
        EDGE,
        /** Into a device (socket, lamp, voltage source). */
        DEVICE,
        /** Into a terminal of a switch. */
        SWITCH
    }

    /**
     * One arm of a cable piece.
     *
     * @param dir         world direction of the arm (always lies in the plane of the piece)
     * @param kind        how the arm reaches its partner
     * @param partnerFace mounting face of the partner piece (cables only)
     * @param reach       how far the arm runs past the block edge into a device, in blocks
     */
    public record Arm(Direction dir, ArmKind kind, Direction partnerFace, float reach) {
    }

    /** How far a cable runs into the base of a bulb socket or a street lamp (their base starts 5 px in). */
    public static final float DEVICE_REACH_SMALL = 5.0F / 16.0F;
    /** How far a cable runs into the body of a voltage source (its body starts 1 px in). */
    public static final float DEVICE_REACH_BODY = 1.0F / 16.0F;

    /**
     * A cell the cable can wrap through: air, replaceable blocks, other cable cells,
     * anything without a collision box (same idea as the original {@code isBlockWrappable}).
     */
    private static boolean isOpenCell(BlockGetter level, BlockPos pos, BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.getBlock() instanceof CableBlock
                || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Cable piece that continues around the outer edge of the supporting block.
     *
     * <p>The piece at {@code pos} lies on {@code face} (support block {@code pos + face}).
     * Running towards {@code dir} it reaches the edge of that support block. When the cell
     * in front is open, the cable carries on down the side of the support block: it is
     * mounted in the diagonal cell {@code pos + dir + face}, on the face pointing back at
     * {@code dir.opposite()} - the very same support block.</p>
     */
    public static ElectricNetwork.Node edgePartner(BlockGetter level, BlockPos pos, CableBlock cable,
                                                   Direction face, Direction dir) {
        return edgePartner(level, pos, cable, face, dir, -1);
    }

    public static ElectricNetwork.Node edgePartner(BlockGetter level, BlockPos pos, CableBlock cable,
                                                   Direction face, Direction dir, int channel) {
        BlockPos front = pos.relative(dir);
        if (!isOpenCell(level, front, level.getBlockState(front))) {
            return null;
        }
        BlockPos diagonal = front.relative(face);
        BlockState diagonalState = level.getBlockState(diagonal);
        if (diagonalState.getBlock() instanceof CableBlock other
                && canLink(cable, channel, other)
                && other.hasFace(diagonalState, dir.getOpposite())) {
            return new ElectricNetwork.Node(diagonal.immutable(), dir.getOpposite(),
                    linkChannel(cable, channel, other));
        }
        return null;
    }

    private static float wallDeviceReach(Block block) {
        if (block instanceof net.fraeric.elnr.common.electric.SpeakerBlock) {
            return 5.0F / 16.0F;
        }
        if (block instanceof net.fraeric.elnr.common.electric.MeterBlock) {
            return 2.0F / 16.0F;
        }
        if (block instanceof net.fraeric.elnr.common.wireless.C4Block) {
            return 4.0F / 16.0F;
        }
        return -1.0F;
    }

    /**
     * @return how far a cable of {@code cable}'s tier lying on {@code face} runs into the
     * device at {@code devicePos}, or a negative number when it does not connect visually
     */
    private static float deviceReach(BlockGetter level, BlockPos devicePos, BlockState device,
                                     CableBlock cable, Direction face) {
        Block block = device.getBlock();
        if (cable.getTier().isSignal()) {
            // Signal cables only reach signal devices (relay, redstone adapter).
            return block instanceof net.fraeric.elnr.common.signal.SignalDevice signalDevice
                    ? signalDevice.cableReach(device, face) : -1.0F;
        }
        if (block instanceof net.fraeric.elnr.common.machine.WindTurbinePoleBlock) {
            return net.fraeric.elnr.common.machine.WindTurbinePoleBlock.reach(device);
        }
        if (block instanceof net.fraeric.elnr.common.machine.SolarPanelLegBlock) {
            return 4.0F / 16.0F;
        }
        if (block instanceof net.fraeric.elnr.common.machine.WindTurbineBlock) {
            return 5.0F / 16.0F;
        }
        if (block instanceof VoltageSourceBlock || block instanceof net.fraeric.elnr.common.machine.ElectricMachineBlock) {
            return DEVICE_REACH_BODY;
        }
        if (block instanceof StandBlock stand) {
            // A stand standing on the same surface takes the strip up to its post.
            if (!canLink(cable, -1, stand.cableType())
                    || device.getValue(StandBlock.FACING).getOpposite() != face) {
                return -1.0F;
            }
            return stand.getReach();
        }
        float wallReach = wallDeviceReach(block);
        if (wallReach > 0.0F) {
            // Speakers, meters and C4 take a strip that lies on their own surface.
            return device.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                    .getOpposite() == face ? wallReach : -1.0F;
        }
        if (block instanceof SocketBlock) {
            // Only a socket lying on the same surface as the cable takes the strip.
            if (device.getValue(SocketBlock.FACING).getOpposite() != face) {
                return -1.0F;
            }
        } else if (block instanceof StreetLampBlock) {
            if (device.getValue(StreetLampBlock.PART) != StreetLampBlock.Part.LOWER) {
                return -1.0F;
            }
            // The pole base is 4 px in from the block edge, the pole itself 6 px.
            BulbHolder lamp = ElectricNetwork.loadAt(level, devicePos);
            if (lamp == null || !lamp.hasCable() || ElectricNetwork.pluggedTier(lamp) != cable.getTier()) {
                return -1.0F;
            }
            return face == Direction.DOWN ? 4.0F / 16.0F : 6.0F / 16.0F;
        } else {
            return -1.0F;
        }
        BulbHolder holder = ElectricNetwork.loadAt(level, devicePos);
        if (holder == null || !holder.hasCable() || ElectricNetwork.pluggedTier(holder) != cable.getTier()) {
            return -1.0F;
        }
        return DEVICE_REACH_SMALL;
    }

    /**
     * @return every arm of the piece on {@code face}, as the renderer draws it. When a
     * straight run and an edge wrap point the same way, only the straight arm is listed.
     */
    public static List<Arm> getCableArms(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        List<Arm> arms = new ArrayList<>();
        if (!(state.getBlock() instanceof CableBlock cable)) {
            return arms;
        }
        // Adjacent faces inside the same block: the arm points along the other face's normal.
        for (Direction other : Direction.values()) {
            if (other.getAxis() == face.getAxis()) {
                continue;
            }
            if (cable.hasFace(state, other)) {
                arms.add(new Arm(other, ArmKind.INNER, other, 0.0F));
            }
        }
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == face.getAxis()) {
                continue;
            }
            BlockPos neighbourPos = pos.relative(direction);
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (neighbour.getBlock() instanceof CableBlock other
                    && canLink(cable, -1, other)
                    && other.hasFace(neighbour, face)) {
                // Straight continuation along the same face into a neighbouring block.
                arms.add(new Arm(direction, ArmKind.STRAIGHT, face, 0.0F));
            } else if (!cable.getTier().isSignal()
                    && neighbour.getBlock() instanceof SwitchBlock switchNeighbour
                    && switchNeighbour.hasTerminalAt(neighbour, direction.getOpposite())
                    && SwitchBlock.mountFace(neighbour) == face) {
                // A switch terminal on the same surface: the strip runs into the switch.
                arms.add(new Arm(direction, ArmKind.SWITCH, face, 0.0F));
            } else {
                float reach = deviceReach(level, neighbourPos, neighbour, cable, face);
                if (reach >= 0.0F) {
                    arms.add(new Arm(direction, ArmKind.DEVICE, face, reach));
                } else if (edgePartner(level, pos, cable, face, direction) != null) {
                    arms.add(new Arm(direction, ArmKind.EDGE, direction.getOpposite(), 0.0F));
                }
            }
        }
        return arms;
    }

    /**
     * @return all in-plane world directions in which this piece has an arm.
     */
    public static EnumSet<Direction> getArms(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        EnumSet<Direction> arms = EnumSet.noneOf(Direction.class);
        for (Arm arm : getCableArms(level, pos, state, face)) {
            arms.add(arm.dir());
        }
        return arms;
    }

    /**
     * @return the pieces electrically connected to the given piece.
     */
    public static List<ElectricNetwork.Node> getConnections(BlockGetter level, ElectricNetwork.Node node) {
        List<ElectricNetwork.Node> connections = new ArrayList<>();
        BlockState state = level.getBlockState(node.pos());
        if (!(state.getBlock() instanceof CableBlock cable) || !cable.hasFace(state, node.face())) {
            return connections;
        }
        for (Direction other : Direction.values()) {
            if (other.getAxis() == node.face().getAxis()) {
                continue;
            }
            if (cable.hasFace(state, other)) {
                connections.add(new ElectricNetwork.Node(node.pos(), other, node.channel()));
            }
        }
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == node.face().getAxis()) {
                continue;
            }
            BlockPos neighbourPos = node.pos().relative(direction);
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (neighbour.getBlock() instanceof CableBlock other
                    && canLink(cable, node.channel(), other)
                    && other.hasFace(neighbour, node.face())) {
                connections.add(new ElectricNetwork.Node(neighbourPos.immutable(), node.face(),
                        linkChannel(cable, node.channel(), other)));
            }
            // Around the outer edge of the support block (may coexist with a straight run).
            ElectricNetwork.Node edge = edgePartner(level, node.pos(), cable, node.face(), direction, node.channel());
            if (edge != null) {
                connections.add(edge);
            }
        }
        // Cable stands are hubs: they join every compatible piece touching them.
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(node.pos().relative(direction));
            if (neighbour.getBlock() instanceof StandBlock stand
                    && canLink(cable, node.channel(), stand.cableType())) {
                connections.add(new ElectricNetwork.Node(node.pos().relative(direction).immutable(),
                        Direction.DOWN, linkChannel(cable, node.channel(), stand.cableType())));
            }
        }
        return connections;
    }

    // ------------------------------------------------------------------
    // Placement / removal
    // ------------------------------------------------------------------

    /**
     * Places a cable piece on the surface described by the clicked block and face.
     *
     * <p>Clicking a solid block mounts a new piece on the face pointing back at it.
     * Clicking an existing cable strip extends that strip through the neighbouring
     * block in the direction the player is looking.</p>
     */
    public static InteractionResult tryPlace(Level level, BlockPos clickedPos, Direction clickedFace,
                                             Vec3 hitLocation, Vec3 look, CableBlock cableBlock) {
        CableTier tier = cableBlock.getTier();
        BlockState clicked = level.getBlockState(clickedPos);

        if (clicked.getBlock() instanceof CableBlock clickedCable) {
            if (clickedCable.getTier() != tier) {
                return InteractionResult.FAIL;
            }
            // The ray usually hits the exposed top/side of a strip, not its mounting
            // plane, so resolve the piece that is closest to the hit point.
            Direction mountFace = nearestFace(clicked, hitLocation, clickedPos);
            if (mountFace == null) {
                return InteractionResult.FAIL;
            }
            Direction extend = inPlaneDirection(mountFace, look);
            if (extend == null) {
                return InteractionResult.FAIL;
            }
            BlockPos targetPos = clickedPos.relative(extend);
            Direction targetFace = mountFace;
            if (!isSupported(level, targetPos, targetFace)) {
                // The support block ends here: carry the cable around its outer edge,
                // down the side of the very same support block.
                BlockState front = level.getBlockState(targetPos);
                if (!isOpenCell(level, targetPos, front)) {
                    return InteractionResult.FAIL;
                }
                targetPos = targetPos.relative(mountFace);
                targetFace = extend.getOpposite();
                if (!isSupported(level, targetPos, targetFace)) {
                    return InteractionResult.FAIL;
                }
            }
            BlockState target = level.getBlockState(targetPos);
            if (target.getBlock() instanceof CableBlock targetCable) {
                if (targetCable != cableBlock || targetCable.hasFace(target, targetFace)) {
                    return InteractionResult.FAIL;
                }
                if (!level.isClientSide) {
                    level.setBlockAndUpdate(targetPos, targetCable.withFace(target, targetFace));
                    ElectricNetwork.updateAt(level, targetPos);
                    playPlaceSound(level, targetPos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!target.canBeReplaced()) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                level.setBlockAndUpdate(targetPos,
                        cableBlock.defaultBlockState().setValue(property(targetFace), true));
                ElectricNetwork.updateAt(level, targetPos);
                playPlaceSound(level, targetPos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Always place into the block in front of the clicked surface, mounted on the face
        // that points back at that surface.
        BlockPos targetPos = clickedPos.relative(clickedFace);
        Direction targetFace = clickedFace.getOpposite();
        if (!isSupported(level, targetPos, targetFace)) {
            return InteractionResult.FAIL;
        }
        BlockState target = level.getBlockState(targetPos);
        if (target.getBlock() instanceof CableBlock targetCable) {
            if (targetCable != cableBlock || targetCable.hasFace(target, targetFace)) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                level.setBlockAndUpdate(targetPos, targetCable.withFace(target, targetFace));
                ElectricNetwork.updateAt(level, targetPos);
                playPlaceSound(level, targetPos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!target.canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            level.setBlockAndUpdate(targetPos, cableBlock.defaultBlockState().setValue(property(targetFace), true));
            ElectricNetwork.updateAt(level, targetPos);
            playPlaceSound(level, targetPos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A piece needs something to cling to: any non-empty block that is not itself just
     * a cable cell. (Devices such as sockets count, so cables can be attached to their
     * sides; a switch does not, cables only run into its two terminals.)
     */
    public static boolean isSupported(BlockGetter level, BlockPos pos, Direction face) {
        BlockState support = level.getBlockState(pos.relative(face));
        return !support.isAir() && !support.canBeReplaced()
                && !(support.getBlock() instanceof CableBlock) && !(support.getBlock() instanceof SwitchBlock);
    }

    private static void playPlaceSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    /**
     * @return the in-plane direction that best matches the player's look vector.
     */
    private static Direction inPlaneDirection(Direction face, Vec3 look) {
        Direction best = null;
        double bestDot = 0.0D;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == face.getAxis()) {
                continue;
            }
            double dot = direction.getStepX() * look.x
                    + direction.getStepY() * look.y
                    + direction.getStepZ() * look.z;
            if (Math.abs(dot) > Math.abs(bestDot)) {
                bestDot = dot;
                best = direction;
            }
        }
        if (best == null) {
            return null;
        }
        return bestDot >= 0.0D ? best : best.getOpposite();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Sneak + empty hand removes the piece on the clicked face.
        if (!player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        Direction face = nearestFace(state, hit.getLocation(), pos);
        if (face == null || !hasFace(state, face)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockState newState = withoutFace(state, face);
            if (countFaces(newState) == 0) {
                level.removeBlock(pos, false);
            } else {
                level.setBlockAndUpdate(pos, newState);
            }
            popResource(level, pos, new ItemStack(asItem()));
            level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.5F, 1.0F);
            ElectricNetwork.updateAt(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        int pieces = countFaces(state);
        if (pieces > 0) {
            drops.add(new ItemStack(asItem(), pieces));
        }
        return drops;
    }

    // ------------------------------------------------------------------
    // Block behaviour
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (BooleanProperty property : FACE_PROPERTIES.values()) {
            builder.add(property);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (Direction face : Direction.values()) {
            if (state.getValue(property(face))) {
                shape = Shapes.or(shape, faceShape(face));
            }
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    private static VoxelShape faceShape(Direction face) {
        return switch (face) {
            case DOWN -> shapeBox(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D);
            case UP -> shapeBox(0.0D, 0.875D, 0.0D, 1.0D, 1.0D, 1.0D);
            case NORTH -> shapeBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.125D);
            case SOUTH -> shapeBox(0.0D, 0.0D, 0.875D, 1.0D, 1.0D, 1.0D);
            case WEST -> shapeBox(0.0D, 0.0D, 0.0D, 0.125D, 1.0D, 1.0D);
            case EAST -> shapeBox(0.875D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        };
    }

    private static VoxelShape shapeBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1, y1, z1, x2, y2, z2);
    }

    /**
     * @return the present cable piece whose mounting plane is closest to the hit location.
     */
    public static Direction nearestFace(BlockState state, Vec3 hit, BlockPos pos) {
        double x = hit.x - pos.getX();
        double y = hit.y - pos.getY();
        double z = hit.z - pos.getZ();
        Direction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            if (!state.getValue(property(face))) {
                continue;
            }
            double distance = switch (face) {
                case DOWN -> y;
                case UP -> 1.0D - y;
                case NORTH -> z;
                case SOUTH -> 1.0D - z;
                case WEST -> x;
                case EAST -> 1.0D - x;
            };
            if (distance < bestDistance) {
                bestDistance = distance;
                best = face;
            }
        }
        return best;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ElectricNetwork.updateAt(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        // Pieces whose supporting block disappeared fall off and drop as items.
        BlockState current = state;
        int dropped = 0;
        for (Direction face : Direction.values()) {
            if (hasFace(current, face) && !isSupported(level, pos, face)) {
                current = withoutFace(current, face);
                dropped++;
            }
        }
        if (dropped > 0) {
            if (countFaces(current) == 0) {
                level.removeBlock(pos, false);
            } else {
                level.setBlockAndUpdate(pos, current);
            }
            popResource(level, pos, new ItemStack(asItem(), dropped));
        }
        ElectricNetwork.updateAt(level, pos);
    }
}
