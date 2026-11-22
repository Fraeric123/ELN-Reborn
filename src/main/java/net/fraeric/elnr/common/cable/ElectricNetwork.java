package net.fraeric.elnr.common.cable;

import net.fraeric.elnr.common.electric.BulbHolder;
import net.fraeric.elnr.common.machine.ElectricMachineBlock;
import net.fraeric.elnr.common.machine.ElectricMachineBlockEntity;
import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.common.electric.SocketBlock;
import net.fraeric.elnr.common.electric.SpeakerBlock;
import net.fraeric.elnr.common.electric.SpeakerBlockEntity;
import net.fraeric.elnr.common.electric.StreetLampBlock;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Simplified DC solver.
 *
 * <p>A cable carries both polarities, so every electrically connected cable network
 * has a single voltage. Voltage sources drive that voltage, sockets and street lamps
 * with a bulb are loads. Switches merge the two networks on their mounting axis when
 * closed.</p>
 *
 * <p>Devices are hubs: a socket or street lamp with a plugged cable connects to every
 * cable piece of the same tier that touches it (no side strips needed), and a load
 * sitting directly next to a voltage source is fed by it without any cable piece in
 * between.</p>
 */
public final class ElectricNetwork {

    public static final int BULB_VOLTAGE = 50;
    public static final double BULB_RESISTANCE = 50.0D;
    /** ELN lamps tolerate 1.3x their nominal voltage before they are destroyed. */
    private static final double BULB_BURN_VOLTAGE = BULB_VOLTAGE * CableTier.VOLTAGE_TOLERANCE;
    private static final double BULB_GLOW_VOLTAGE = 25.0D;

    private static boolean updating = false;
    private static final Set<BlockPos> pending = new HashSet<>();

    private ElectricNetwork() {
    }

    public record Node(BlockPos pos, Direction face, int channel) {
        /** A piece of an ordinary cable block (only bundled cables use a wire channel). */
        public Node(BlockPos pos, Direction face) {
            this(pos, face, -1);
        }
    }

    /**
     * Recomputes every network that touches {@code pos} (or one of its six neighbours).
     */
    public static void updateAt(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        if (updating) {
            pending.add(pos.immutable());
            return;
        }
        updating = true;
        try {
            doUpdate(level, pos);
        } finally {
            updating = false;
        }
        if (!pending.isEmpty()) {
            List<BlockPos> queued = new ArrayList<>(pending);
            pending.clear();
            for (BlockPos queuedPos : queued) {
                updateAt(level, queuedPos);
            }
        }
    }

    private static void doUpdate(Level level, BlockPos pos) {
        Set<Node> visited = new HashSet<>();
        List<BlockPos> starts = new ArrayList<>(7);
        starts.add(pos);
        for (Direction direction : Direction.values()) {
            starts.add(pos.relative(direction));
        }
        // Cables also join around block edges, through the twelve edge neighbours.
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a.getAxis().ordinal() < b.getAxis().ordinal()) {
                    starts.add(pos.relative(a).relative(b));
                }
            }
        }

        // Loads that lost their link (cable unplugged, cable pieces removed) must go
        // dark even though no network reaches them any more.
        for (BlockPos start : starts) {
            BlockState state = level.getBlockState(start);
            if (!(state.getBlock() instanceof SocketBlock) && !(state.getBlock() instanceof StreetLampBlock)) {
                continue;
            }
            BlockPos loadPos = loadBase(level, start);
            if (isLoadLit(level, loadPos) && !isLoadLinked(level, loadPos)) {
                setLoadLit(level, loadPos, false);
            }
        }

        // Sound blocks without any cable next to them listen to a neighbouring source only.
        for (BlockPos start : starts) {
            if (level.getBlockState(start).getBlock() instanceof SpeakerBlock
                    && level.getBlockEntity(start) instanceof SpeakerBlockEntity speaker
                    && !hasAdjacentCable(level, start, null, true)) {
                speaker.setSignal(level, Math.abs(adjacentSourceVoltage(level, start)) >= SpeakerBlock.SIGNAL_VOLTAGE);
            }
        }

        for (BlockPos start : starts) {
            BlockState state = level.getBlockState(start);
            if (state.getBlock() instanceof StandBlock stand) {
                for (int channel : stand.channels()) {
                    Node node = new Node(start.immutable(), Direction.DOWN, channel);
                    if (!visited.contains(node)) {
                        solve(level, node, visited);
                    }
                }
                continue;
            }
            if (!(state.getBlock() instanceof CableBlock cable)) {
                continue;
            }
            for (Direction face : Direction.values()) {
                if (cable.hasFace(state, face)) {
                    for (int channel : cable.channels()) {
                        Node node = new Node(start.immutable(), face, channel);
                        if (!visited.contains(node)) {
                            solve(level, node, visited);
                        }
                    }
                }
            }
        }

        // Loads fed straight from a neighbouring voltage source without any cable piece.
        for (BlockPos start : starts) {
            BulbHolder holder = loadAt(level, start);
            if (holder == null || !holder.hasCable() || !holder.hasBulb()) {
                continue;
            }
            if (hasAdjacentCable(level, start, pluggedTier(holder))) {
                continue; // Part of a cable network, already handled by the solver.
            }
            double voltage = adjacentSourceVoltage(level, start);
            if (voltage != 0.0D) {
                applyToLoad(level, start, voltage);
            }
        }
    }

    // ------------------------------------------------------------------
    // Devices (loads)
    // ------------------------------------------------------------------

    /**
     * @return the bulb holder (socket or street lamp base) at pos, or null.
     */
    public static @Nullable BulbHolder loadAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof SocketBlock) {
            return level.getBlockEntity(pos) instanceof BulbHolder holder ? holder : null;
        }
        if (block instanceof StreetLampBlock) {
            BlockPos base = StreetLampBlock.basePos(pos, state);
            return level.getBlockEntity(base) instanceof BulbHolder holder ? holder : null;
        }
        return null;
    }

    /** @return the position a load is tracked at (the lamp base for street lamps). */
    public static BlockPos loadBase(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StreetLampBlock) {
            return StreetLampBlock.basePos(pos, state);
        }
        return pos.immutable();
    }

    private static boolean isLoadLit(BlockGetter level, BlockPos loadPos) {
        BlockState state = level.getBlockState(loadPos);
        if (state.getBlock() instanceof SocketBlock) {
            return state.getValue(SocketBlock.LIT);
        }
        if (state.getBlock() instanceof StreetLampBlock) {
            return StreetLampBlock.isHeadLit(level, loadPos);
        }
        return false;
    }

    private static void setLoadLit(Level level, BlockPos loadPos, boolean lit) {
        BlockState state = level.getBlockState(loadPos);
        if (state.getBlock() instanceof SocketBlock && state.getValue(SocketBlock.LIT) != lit) {
            level.setBlock(loadPos, state.setValue(SocketBlock.LIT, lit), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof StreetLampBlock) {
            StreetLampBlock.setHeadLit(level, loadPos, lit);
        }
    }

    /**
     * A load is linked when its plugged cable can reach the network: any adjacent
     * cable piece of the same tier, or a voltage source right next to it.
     */
    public static boolean isLoadLinked(BlockGetter level, BlockPos loadPos) {
        BulbHolder holder = loadAt(level, loadPos);
        if (holder == null || !holder.hasCable()) {
            return false;
        }
        return hasAdjacentCable(level, loadPos, pluggedTier(holder))
                || adjacentSourceVoltage(level, loadPos) != 0.0D;
    }

    private static boolean hasAdjacentCable(BlockGetter level, BlockPos pos, @Nullable CableTier tier, boolean anyTier) {
        for (Direction direction : Direction.values()) {
            Block neighbour = level.getBlockState(pos.relative(direction)).getBlock();
            if (neighbour instanceof CableBlock || neighbour instanceof StandBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAdjacentCable(BlockGetter level, BlockPos pos, @Nullable CableTier tier) {
        if (tier == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockState state = level.getBlockState(pos.relative(direction));
            if (state.getBlock() instanceof CableBlock cable && cable.getTier() == tier) {
                return true;
            }
            if (state.getBlock() instanceof StandBlock stand && stand.getTier() == tier) {
                return true;
            }
        }
        return false;
    }

    private static double adjacentSourceVoltage(BlockGetter level, BlockPos pos) {
        double voltage = 0.0D;
        for (Direction direction : Direction.values()) {
            BlockPos sourcePos = pos.relative(direction);
            if (level.getBlockState(sourcePos).getBlock() instanceof VoltageSourceBlock
                    && level.getBlockEntity(sourcePos) instanceof VoltageSourceBlockEntity source) {
                if (Math.abs(source.getVoltage()) > Math.abs(voltage)) {
                    voltage = source.getVoltage();
                }
            }
        }
        return voltage;
    }

    /** @return the cable block plugged into the holder, or null. */
    public static @Nullable CableBlock pluggedBlock(BulbHolder holder) {
        ItemStack cable = holder.getCable();
        if (cable.getItem() instanceof CableItem item && item.getBlock() instanceof CableBlock block) {
            return block;
        }
        return null;
    }

    /** @return the tier of the cable plugged into the holder, or null. */
    public static @Nullable CableTier pluggedTier(BulbHolder holder) {
        ItemStack cable = holder.getCable();
        if (cable.getItem() instanceof CableItem item && item.getBlock() instanceof CableBlock block) {
            return block.getTier();
        }
        return null;
    }

    /**
     * Snapshot of one electrically connected network, produced without changing the world.
     *
     * @param current total current drawn by all working bulbs of the network, in amperes
     */
    public record Analysis(Set<Node> pieces, Set<BlockPos> sources, Set<BlockPos> loads,
                           Set<BlockPos> closedSwitches, Set<BlockPos> openSwitches,
                           double voltage, double current, Set<BlockPos> speakers,
                           Set<BlockPos> machines, double machineCurrent) {
    }

    /**
     * Read-only analysis used by the multimeter and thermometer.
     */
    public static Analysis analyze(BlockGetter level, Node start) {
        BlockState startState = level.getBlockState(start.pos());
        if (start.channel() < 0 && startState.getBlock() instanceof StandBlock bundledStand && bundledStand.isBundled()) {
            Analysis best = null;
            for (int channel : bundledStand.channels()) {
                Analysis analysis = collect(level, new Node(start.pos(), start.face(), channel), new HashSet<>());
                if (best == null || Math.abs(analysis.voltage()) > Math.abs(best.voltage())) {
                    best = analysis;
                }
            }
            return best;
        }
        if (start.channel() < 0 && startState.getBlock() instanceof CableBlock bundle && bundle.isBundled()) {
            // A bundle holds 16 wires: report the one with the highest voltage.
            Analysis best = null;
            for (int channel : bundle.channels()) {
                Analysis analysis = collect(level, new Node(start.pos(), start.face(), channel), new HashSet<>());
                if (best == null || Math.abs(analysis.voltage()) > Math.abs(best.voltage())) {
                    best = analysis;
                }
            }
            return best;
        }
        return collect(level, start, new HashSet<>());
    }

    /** Network a machine is wired to (through any cable piece touching it), or null when it has none. */
    public static @Nullable Analysis analyzeMachine(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos p = pos.relative(direction);
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof CableBlock cable && !cable.isBundled()) {
                for (Direction face : Direction.values()) {
                    if (cable.hasFace(state, face)) {
                        return collect(level, new Node(p.immutable(), face), new HashSet<>());
                    }
                }
            } else if (state.getBlock() instanceof StandBlock stand && !stand.cableType().isBundled()) {
                return collect(level, new Node(p.immutable(), Direction.DOWN, -1), new HashSet<>());
            }
        }
        return null;
    }

    private static Analysis collect(BlockGetter level, Node start, Set<Node> visited) {
        Set<Node> pieces = new LinkedHashSet<>();
        Set<BlockPos> sources = new LinkedHashSet<>();
        Set<BlockPos> loads = new LinkedHashSet<>();
        Set<BlockPos> closedSwitches = new LinkedHashSet<>();
        Set<BlockPos> openSwitches = new LinkedHashSet<>();
        Set<BlockPos> speakers = new LinkedHashSet<>();
        Set<BlockPos> machines = new LinkedHashSet<>();

        Deque<Node> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            pieces.add(node);

            BlockState state = level.getBlockState(node.pos());
            // A stand stands in for a cable of its own type; its connections are its own.
            boolean standNode = state.getBlock() instanceof StandBlock;
            CableBlock cable;
            if (standNode) {
                cable = ((StandBlock) state.getBlock()).cableType();
            } else if (state.getBlock() instanceof CableBlock piece && piece.hasFace(state, node.face())) {
                cable = piece;
            } else {
                continue;
            }

            List<Node> joined = standNode ? StandBlock.getConnections(level, node)
                    : CableBlock.getConnections(level, node);
            for (Node connection : joined) {
                if (visited.add(connection)) {
                    queue.add(connection);
                }
            }

            if (cable.isBundled()) {
                continue; // Bundles only carry wires, they do not touch devices.
            }

            // Devices around the piece; devices are hubs, so any neighbouring device
            // connects to the network.
            for (Direction direction : Direction.values()) {
                BlockPos devicePos = node.pos().relative(direction);
                BlockState deviceState = level.getBlockState(devicePos);
                Block device = deviceState.getBlock();

                if (device instanceof VoltageSourceBlock) {
                    if (sources.add(devicePos)) {
                        addPiecesTouching(level, devicePos, EnumSet.allOf(Direction.class), null, null, queue, visited);
                    }
                } else if (device instanceof ElectricMachineBlock) {
                    if (machines.add(devicePos)) {
                        addPiecesTouching(level, devicePos, EnumSet.allOf(Direction.class), null, null, queue, visited);
                    }
                } else if (device instanceof SpeakerBlock) {
                    // Sound blocks only listen to the voltage of the network they touch.
                    speakers.add(devicePos);
                } else if (device instanceof SocketBlock) {
                    discoverLoad(level, devicePos, sources, loads, queue, visited);
                } else if (device instanceof StreetLampBlock) {
                    discoverLoad(level, loadBase(level, devicePos), sources, loads, queue, visited);
                } else if (device instanceof SwitchBlock switchBlock) {
                    // Original ELN: the switch only has two terminals, at both ends of
                    // its front axis. A cable joins it when it lies on the same surface
                    // and runs into a terminal; pieces beside it do not touch it.
                    Direction entered = direction.getOpposite();
                    if (standNode || !switchBlock.hasTerminalAt(deviceState, entered)
                            || node.face() != SwitchBlock.mountFace(deviceState)) {
                        continue;
                    }
                    // A relay's coil input is only read by the relay itself, so it neither joins
                    // the contacts nor counts as a rated switch terminal.
                    if (switchBlock instanceof net.fraeric.elnr.common.electric.RelayBlock
                            && entered == net.fraeric.elnr.common.electric.RelayBlock.coilSide(deviceState)) {
                        continue;
                    }
                    // Like the original ELN switch, the block is rated even while open: the
                    // voltage watchdogs sit on all terminals and blow it up on overvoltage.
                    Direction[] exits = switchBlock.passThrough(deviceState, entered);
                    if (exits.length == 0) {
                        openSwitches.add(devicePos);
                    } else {
                        closedSwitches.add(devicePos);
                        for (Direction exit : exits) {
                            BlockPos farPos = devicePos.relative(exit);
                            BlockState farState = level.getBlockState(farPos);
                            if (farState.getBlock() instanceof CableBlock farCable
                                    && farCable.getTier() == cable.getTier() && !farCable.isBundled()
                                    && farCable.hasFace(farState, node.face())) {
                                Node far = new Node(farPos.immutable(), node.face());
                                if (visited.add(far)) {
                                    queue.add(far);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voltage of the network.
        double voltage = 0.0D;
        for (BlockPos sourcePos : sources) {
            if (level.getBlockEntity(sourcePos) instanceof VoltageSourceBlockEntity source) {
                if (Math.abs(source.getVoltage()) > Math.abs(voltage)) {
                    voltage = source.getVoltage();
                }
            }
        }

        // Machines: generators / converters force a voltage, accumulators set one when nothing else does.
        double machineFixed = 0.0D;
        double machineStore = 0.0D;
        double machineCurrent = 0.0D;
        for (BlockPos machinePos : machines) {
            if (level.getBlockEntity(machinePos) instanceof ElectricMachineBlockEntity machine) {
                machineFixed = Math.max(machineFixed, machine.fixedSourceVoltage());
                machineStore = Math.max(machineStore, machine.storageVoltage());
                machineCurrent += machine.loadCurrent();
            }
        }
        if (machineFixed > Math.abs(voltage)) {
            voltage = machineFixed;
        } else if (voltage == 0.0D) {
            voltage = machineStore;
        }

        // Current drawn by bulbs that survive this voltage.
        double current = 0.0D;
        for (BlockPos loadPos : loads) {
            BulbHolder holder = loadAt(level, loadPos);
            if (holder != null && holder.hasBulb() && Math.abs(voltage) > 0
                    && Math.abs(voltage) <= burnVoltage(holder)) {
                current += Math.abs(voltage) / BulbItem.resistanceOf(holder.getBulb());
            }
        }
        return new Analysis(pieces, sources, loads, closedSwitches, openSwitches, voltage, current, speakers, machines, machineCurrent);
    }

    private static void discoverLoad(BlockGetter level, BlockPos loadPos, Set<BlockPos> sources,
                                     Set<BlockPos> loads, Deque<Node> queue, Set<Node> visited) {
        BulbHolder holder = loadAt(level, loadPos);
        if (holder == null || !holder.hasCable()) {
            return;
        }
        CableTier tier = pluggedTier(holder);
        if (tier == null) {
            return;
        }
        if (loads.add(loadPos)) {
            addPiecesTouching(level, loadPos, EnumSet.allOf(Direction.class), tier, pluggedBlock(holder),
                    queue, visited);
            // A voltage source right next to the load feeds it directly.
            for (Direction direction : Direction.values()) {
                BlockPos sourcePos = loadPos.relative(direction);
                if (level.getBlockState(sourcePos).getBlock() instanceof VoltageSourceBlock
                        && sources.add(sourcePos)) {
                    addPiecesTouching(level, sourcePos, EnumSet.allOf(Direction.class), null, null, queue, visited);
                }
            }
        }
    }

    private static void solve(Level level, Node start, Set<Node> visited) {
        Analysis analysis = collect(level, start, visited);
        double voltage = analysis.voltage();

        // Bulbs of every load in the network, plus the machines' own draw.
        double totalCurrent = analysis.machineCurrent();
        for (BlockPos loadPos : analysis.loads()) {
            totalCurrent += applyToLoad(level, loadPos, voltage);
        }

        // Overloaded or overvoltage cable pieces burn away (no drops).
        List<Node> toBurn = new ArrayList<>();
        for (Node node : analysis.pieces()) {
            BlockState state = level.getBlockState(node.pos());
            CableTier pieceTier;
            if (state.getBlock() instanceof StandBlock stand) {
                pieceTier = stand.getTier();
            } else if (state.getBlock() instanceof CableBlock cable && cable.hasFace(state, node.face())) {
                pieceTier = cable.getTier();
            } else {
                continue;
            }
            boolean overCurrent = totalCurrent > pieceTier.getBreakdownCurrent();
            boolean overVoltage = Math.abs(voltage) > pieceTier.getBreakdownVoltage();
            if (overCurrent || overVoltage) {
                toBurn.add(node);
            }
        }
        for (Node node : toBurn) {
            BlockState state = level.getBlockState(node.pos());
            if (state.getBlock() instanceof StandBlock) {
                // An overloaded stand burns away together with its overhead lines.
                level.removeBlock(node.pos(), false);
                burnEffects(level, node.pos());
                continue;
            }
            if (!(state.getBlock() instanceof CableBlock cable) || !cable.hasFace(state, node.face())) {
                continue;
            }
            BlockState newState = cable.withoutFace(state, node.face());
            if (CableBlock.countFaces(newState) == 0) {
                level.removeBlock(node.pos(), false);
            } else {
                level.setBlockAndUpdate(node.pos(), newState);
            }
            burnEffects(level, node.pos());
        }

        boolean signal = Math.abs(voltage) >= SpeakerBlock.SIGNAL_VOLTAGE;
        for (BlockPos speakerPos : analysis.speakers()) {
            if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
                speaker.setSignal(level, signal);
            }
        }

        applySwitchHazards(level, analysis, totalCurrent);
    }

    // ------------------------------------------------------------------
    // Switch hazards (ported from the original ELN electrical switch)
    // ------------------------------------------------------------------

    /**
     * Destroys switches that leave their ratings, exactly like the original:
     * the current watchdog melts a closed switch above 1.25x the current rating
     * (ElectricalSafety.CONTINUOUS_TO_DAMAGE_CURRENT_FACTOR), the voltage
     * watchdogs on both terminals blow it up above 1.3x the nominal voltage
     * (VoltageStateWatchDog), even while it is open. The original tolerates the
     * overload for 0.25 s (SHORT_OVERLOAD_GRACE_SECONDS); this solver is event
     * driven, so the damage is applied on the spot.
     *
     * <p>The ratings are those of the switch block itself (300 V / 15 A for the small
     * switches, 1600 V / 5 A for the big selector switch).</p>
     *
     * <p>Like the cable burn check above, the current through the switch is the
     * total network current, which is exact for the usual source - switch - loads
     * topology.</p>
     */
    private static void applySwitchHazards(Level level, Analysis analysis, double totalCurrent) {
        for (BlockPos switchPos : analysis.closedSwitches()) {
            if (!(level.getBlockState(switchPos).getBlock() instanceof SwitchBlock rated)) {
                continue;
            }
            if (Math.abs(analysis.voltage()) > rated.getRatedVoltage() * CableTier.VOLTAGE_TOLERANCE) {
                explodeSwitch(level, switchPos);
            } else if (totalCurrent > rated.getRatedCurrent() * CableTier.SWITCH_DAMAGE_CURRENT_FACTOR) {
                meltSwitch(level, switchPos);
            }
        }
        for (BlockPos switchPos : analysis.openSwitches()) {
            if (!(level.getBlockState(switchPos).getBlock() instanceof SwitchBlock rated)) {
                continue;
            }
            if (Math.abs(analysis.voltage()) > rated.getRatedVoltage() * CableTier.VOLTAGE_TOLERANCE) {
                explodeSwitch(level, switchPos);
            }
        }
    }

    /** CableMelt of the original: the element is destroyed without a drop. */
    private static void meltSwitch(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        burnEffects(level, pos);
    }

    /** WorldExplosion.cableExplosion() of the original: a 1.5 strength explosion. */
    private static void explodeSwitch(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                1.5F, Level.ExplosionInteraction.BLOCK);
    }

    private static void burnEffects(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    6, 0.2D, 0.2D, 0.2D, 0.02D);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    2, 0.1D, 0.1D, 0.1D, 0.01D);
            serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    /**
     * Applies the network voltage to one load: glows, burns out the bulb on
     * overvoltage and updates the block state.
     *
     * @return the current the load draws, in amperes
     */
    private static double applyToLoad(Level level, BlockPos loadPos, double voltage) {
        BulbHolder holder = loadAt(level, loadPos);
        if (holder == null) {
            return 0.0D;
        }
        boolean hasBulb = holder.hasBulb();
        boolean lit = hasBulb && Math.abs(voltage) >= 0.5D * BulbItem.voltageOf(holder.getBulb());

        if (hasBulb && Math.abs(voltage) > burnVoltage(holder)) {
            holder.breakBulb(level, loadPos);
            lit = false;
        }

        setLoadLit(level, loadPos, lit);

        boolean survives = Math.abs(voltage) > 0 && hasBulb && Math.abs(voltage) <= burnVoltage(holder);
        return survives ? Math.abs(voltage) / BulbItem.resistanceOf(holder.getBulb()) : 0.0D;
    }

    /** A bulb is destroyed above 1.3x its rated voltage. */
    public static double burnVoltage(BulbHolder holder) {
        return BulbItem.voltageOf(holder.getBulb()) * CableTier.VOLTAGE_TOLERANCE;
    }

    /**
     * Adds every cable piece (and stand) of a matching type in the given directions to the
     * network, regardless of the piece's own orientation (devices are hubs).
     *
     * @param tier when set, only cables of this tier join
     * @param plug when set, only cables the plugged cable can join (same tier, colour rules)
     */
    private static void addPiecesTouching(BlockGetter level, BlockPos devicePos, EnumSet<Direction> directions,
                                          @Nullable CableTier tier, @Nullable CableBlock plug,
                                          Deque<Node> queue, Set<Node> visited) {
        for (Direction direction : directions) {
            BlockPos cablePos = devicePos.relative(direction);
            BlockState state = level.getBlockState(cablePos);
            if (state.getBlock() instanceof StandBlock stand) {
                CableBlock type = stand.cableType();
                if (type.isBundled() || (tier != null && type.getTier() != tier)
                        || (plug != null && !CableBlock.canLink(plug, -1, type))) {
                    continue;
                }
                Node node = new Node(cablePos.immutable(), Direction.DOWN, -1);
                if (visited.add(node)) {
                    queue.add(node);
                }
                continue;
            }
            if (!(state.getBlock() instanceof CableBlock cable)) {
                continue;
            }
            if (tier != null && cable.getTier() != tier) {
                continue;
            }
            if (plug != null && !CableBlock.canLink(plug, -1, cable)) {
                continue;
            }
            if (cable.isBundled()) {
                continue;
            }
            for (Direction face : Direction.values()) {
                if (cable.hasFace(state, face)) {
                    Node node = new Node(cablePos.immutable(), face);
                    if (visited.add(node)) {
                        queue.add(node);
                    }
                }
            }
        }
    }
}
