package net.fraeric.elnr.common.signal;

import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.ElectricNetwork.Node;
import net.fraeric.elnr.common.cable.StandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solver of the signal cables.
 *
 * <p>A signal network is a set of signal cable pieces (and stands) that are wired together with the
 * very same rules as the power cables (same colour or bare, bundles, stands, overhead lines). It
 * carries a single boolean: {@code true} as soon as one {@link SignalDevice} next to it emits a signal.
 * Nothing else is transported - no voltage, no current, nothing can burn.</p>
 *
 * <p>The electric solver calls {@link #update} whenever something around a position changed, so
 * signal networks are recomputed on exactly the same events as the electric ones.</p>
 */
public final class SignalNetwork {

    private SignalNetwork() {
    }

    /**
     * Snapshot of one signal network.
     *
     * @param sources   positions of the devices that currently emit {@code true}
     * @param receivers positions of the devices that listen to this network
     * @param signal    the value of the network
     */
    public record Result(Set<Node> pieces, Set<BlockPos> sources, Set<BlockPos> receivers, boolean signal) {
    }

    /** @return true when the stand carries signals. */
    private static boolean isSignal(StandBlock stand) {
        return stand.getTier().isSignal();
    }

    /** @return the network the given piece belongs to (read-only). */
    public static Result collect(BlockGetter level, Node start) {
        Set<Node> pieces = new LinkedHashSet<>();
        Set<BlockPos> sources = new LinkedHashSet<>();
        Set<BlockPos> receivers = new LinkedHashSet<>();
        boolean signal = false;

        Deque<Node> queue = new ArrayDeque<>();
        queue.add(start);
        pieces.add(start);

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            BlockState state = level.getBlockState(node.pos());

            CableBlock cable;
            List<Node> joined;
            if (state.getBlock() instanceof StandBlock stand) {
                if (!isSignal(stand)) {
                    continue;
                }
                cable = stand.cableType();
                joined = StandBlock.getConnections(level, node);
            } else if (state.getBlock() instanceof CableBlock piece && piece.getTier().isSignal()
                    && piece.hasFace(state, node.face())) {
                cable = piece;
                joined = CableBlock.getConnections(level, node);
            } else {
                continue;
            }

            for (Node connection : joined) {
                if (pieces.add(connection)) {
                    queue.add(connection);
                }
            }
            if (cable.isBundled()) {
                continue; // Bundles only carry wires, they do not touch devices.
            }

            for (Direction direction : Direction.values()) {
                BlockPos devicePos = node.pos().relative(direction);
                BlockState deviceState = level.getBlockState(devicePos);
                if (deviceState.getBlock() instanceof SignalDevice device) {
                    if (device.emitsSignal(level, devicePos, deviceState)) {
                        sources.add(devicePos.immutable());
                        signal = true;
                    }
                    if (device.receivesSignal(deviceState)) {
                        receivers.add(devicePos.immutable());
                    }
                }
            }
        }
        return new Result(pieces, sources, receivers, signal);
    }

    /**
     * Recomputes every signal network that touches one of the given positions and tells the
     * devices that listen to them. Called by the electric solver.
     */
    public static void update(Level level, List<BlockPos> starts) {
        Map<Node, Boolean> known = new HashMap<>();
        Set<BlockPos> receivers = new LinkedHashSet<>();
        for (BlockPos start : starts) {
            BlockState state = level.getBlockState(start);
            if (state.getBlock() instanceof StandBlock stand) {
                if (isSignal(stand)) {
                    for (int channel : stand.channels()) {
                        visit(level, new Node(start.immutable(), Direction.DOWN, channel), known, receivers);
                    }
                }
            } else if (state.getBlock() instanceof CableBlock cable) {
                if (cable.getTier().isSignal()) {
                    for (Direction face : Direction.values()) {
                        if (cable.hasFace(state, face)) {
                            for (int channel : cable.channels()) {
                                visit(level, new Node(start.immutable(), face, channel), known, receivers);
                            }
                        }
                    }
                }
            } else if (state.getBlock() instanceof SignalDevice device && device.receivesSignal(state)) {
                receivers.add(start.immutable());
            }
        }
        for (BlockPos pos : receivers) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SignalDevice device && device.receivesSignal(state)) {
                device.onSignal(level, pos, state, read(level, pos, known));
            }
        }
    }

    private static void visit(BlockGetter level, Node node, Map<Node, Boolean> known, Set<BlockPos> receivers) {
        if (known.containsKey(node)) {
            return;
        }
        Result result = collect(level, node);
        for (Node piece : result.pieces()) {
            known.put(piece, result.signal());
        }
        receivers.addAll(result.receivers());
    }

    /**
     * @return the value a device sees: the logical OR of every signal network touching it
     * (a device is a hub, so any non bundled signal piece next to it counts)
     */
    public static boolean read(BlockGetter level, BlockPos pos) {
        return read(level, pos, new HashMap<>());
    }

    private static boolean read(BlockGetter level, BlockPos pos, Map<Node, Boolean> known) {
        boolean signal = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            BlockState state = level.getBlockState(neighbour);
            if (state.getBlock() instanceof StandBlock stand) {
                if (isSignal(stand) && !stand.isBundled()) {
                    signal |= value(level, new Node(neighbour.immutable(), Direction.DOWN, -1), known);
                }
            } else if (state.getBlock() instanceof CableBlock cable
                    && cable.getTier().isSignal() && !cable.isBundled()) {
                for (Direction face : Direction.values()) {
                    if (cable.hasFace(state, face)) {
                        signal |= value(level, new Node(neighbour.immutable(), face, -1), known);
                    }
                }
            }
        }
        return signal;
    }

    private static boolean value(BlockGetter level, Node node, Map<Node, Boolean> known) {
        Boolean cached = known.get(node);
        if (cached == null) {
            Result result = collect(level, node);
            for (Node piece : result.pieces()) {
                known.put(piece, result.signal());
            }
            cached = result.signal();
        }
        return cached;
    }

    /** @return the network state shown by the multimeter for the given cable/stand/device, or null. */
    public static Boolean probe(BlockGetter level, BlockPos pos, net.minecraft.world.phys.Vec3 hit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StandBlock stand) {
            return isSignal(stand) ? probeNode(level, new Node(pos.immutable(), Direction.DOWN, -1), stand.isBundled()) : null;
        }
        if (state.getBlock() instanceof CableBlock cable && cable.getTier().isSignal()) {
            Direction face = CableBlock.nearestFace(state, hit, pos);
            return face == null ? null : probeNode(level, new Node(pos.immutable(), face, -1), cable.isBundled());
        }
        if (state.getBlock() instanceof SignalDevice device) {
            if (device.receivesSignal(state)) {
                return read(level, pos);
            }
            return device.emitsSignal(level, pos, state);
        }
        return null;
    }

    private static Boolean probeNode(BlockGetter level, Node node, boolean bundled) {
        if (!bundled) {
            return collect(level, node).signal();
        }
        // A bundle holds 16 wires: the multimeter shows whether any of them is on.
        for (int channel = 0; channel < 16; channel++) {
            if (collect(level, new Node(node.pos(), node.face(), channel)).signal()) {
                return true;
            }
        }
        return false;
    }
}
