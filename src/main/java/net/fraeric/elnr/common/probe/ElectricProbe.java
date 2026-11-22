package net.fraeric.elnr.common.probe;

import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.CableTier;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.electric.BulbHolder;
import net.fraeric.elnr.common.electric.SocketBlock;
import net.fraeric.elnr.common.electric.StreetLampBlock;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Measures cables and devices for the multimeter and the thermometer.
 *
 * <p>The mod uses a simplified DC solver, so currents are the sums of the bulb loads
 * of a network and temperatures are the steady state of ambient temperature plus
 * resistive heating.</p>
 */
public final class ElectricProbe {

    /** Temperature rise of a cable carrying exactly its rated current, in kelvin. */
    private static final double CABLE_RATED_RISE = 60.0D;
    /** Temperature rise of a bulb at its rated voltage, in kelvin. */
    private static final double BULB_RATED_RISE = 130.0D;

    private ElectricProbe() {
    }

    /**
     * @param name        display name of the measured part
     * @param voltage     volts
     * @param current     amperes
     * @param power       watts
     * @param temperature degrees Celsius
     */
    public record Reading(Component name, double voltage, double current, double power, double temperature) {
    }

    public static Optional<Reading> measure(Level level, BlockPos pos, Vec3 hit) {
        BlockState state = level.getBlockState(pos);
        double ambient = ambientTemperature(level, pos);

        if (state.getBlock() instanceof CableBlock cable) {
            Direction face = CableBlock.nearestFace(state, hit, pos);
            if (face == null) {
                return Optional.empty();
            }
            ElectricNetwork.Analysis analysis = ElectricNetwork.analyze(level, new ElectricNetwork.Node(pos, face));
            double voltage = analysis.voltage();
            double current = analysis.current();
            double ratio = current / cable.getTier().getMaxCurrent();
            return Optional.of(new Reading(state.getBlock().getName(), voltage, current,
                    Math.abs(voltage * current), ambient + CABLE_RATED_RISE * ratio * ratio));
        }

        if (state.getBlock() instanceof net.fraeric.elnr.common.cable.StandBlock stand) {
            ElectricNetwork.Analysis analysis = ElectricNetwork.analyze(level,
                    new ElectricNetwork.Node(pos, Direction.DOWN));
            double voltage = analysis.voltage();
            double current = analysis.current();
            double ratio = current / stand.getTier().getMaxCurrent();
            return Optional.of(new Reading(state.getBlock().getName(), voltage, current,
                    Math.abs(voltage * current), ambient + CABLE_RATED_RISE * ratio * ratio));
        }

        if (state.getBlock() instanceof VoltageSourceBlock
                && level.getBlockEntity(pos) instanceof VoltageSourceBlockEntity source) {
            List<ElectricNetwork.Analysis> networks = networksAround(level, pos, List.of(Direction.values()), null);
            double current = sumCurrent(networks);
            double voltage = source.getVoltage();
            double power = Math.abs(voltage * current);
            return Optional.of(new Reading(state.getBlock().getName(), voltage, current, power,
                    ambient + 0.01D * power));
        }

        BulbHolder load = ElectricNetwork.loadAt(level, pos);
        if (load != null && (state.getBlock() instanceof SocketBlock || state.getBlock() instanceof StreetLampBlock)) {
            List<ElectricNetwork.Analysis> networks = load.hasCable()
                    ? networksAround(level, ElectricNetwork.loadBase(level, pos),
                            List.of(Direction.values()), ElectricNetwork.pluggedTier(load))
                    : List.of();
            double voltage = networks.isEmpty() ? 0.0D : networks.get(0).voltage();
            double current = 0.0D;
            if (load.hasBulb() && Math.abs(voltage) > 0
                    && Math.abs(voltage) <= ElectricNetwork.burnVoltage(load)) {
                current = Math.abs(voltage) / net.fraeric.elnr.common.item.BulbItem.resistanceOf(load.getBulb());
            }
            double ratio = voltage / (load.hasBulb()
                    ? net.fraeric.elnr.common.item.BulbItem.voltageOf(load.getBulb()) : ElectricNetwork.BULB_VOLTAGE);
            double temperature = load.hasBulb() ? ambient + BULB_RATED_RISE * ratio * ratio : ambient;
            return Optional.of(new Reading(state.getBlock().getName(), voltage, current,
                    Math.abs(voltage * current), temperature));
        }

        if (state.getBlock() instanceof SwitchBlock switchBlock) {
            List<ElectricNetwork.Analysis> networks = switchNetworks(level, pos, state);
            double voltage;
            double current = 0.0D;
            if (switchBlock.isOpen(state)) {
                // Open switch: voltage across the contacts, no current.
                voltage = 0.0D;
                if (networks.size() == 2) {
                    voltage = Math.abs(networks.get(0).voltage() - networks.get(1).voltage());
                } else if (networks.size() == 1) {
                    voltage = Math.abs(networks.get(0).voltage());
                }
            } else {
                voltage = 0.0D;
                current = sumCurrent(networks);
            }
            return Optional.of(new Reading(state.getBlock().getName(), voltage, current, 0.0D,
                    ambient + 0.5D * current * current));
        }

        return Optional.empty();
    }

    /**
     * Distinct networks touching the given sides of a device (a network reached from
     * several sides is only counted once). Devices are hubs, so any cable piece next
     * to them counts, not only pieces pointing at them; when {@code tier} is given,
     * only pieces of that cable tier are considered.
     */
    private static List<ElectricNetwork.Analysis> networksAround(Level level, BlockPos devicePos,
                                                                  List<Direction> sides, @org.jetbrains.annotations.Nullable CableTier tier) {
        List<ElectricNetwork.Analysis> result = new ArrayList<>();
        for (Direction side : sides) {
            BlockPos cablePos = devicePos.relative(side);
            BlockState cableState = level.getBlockState(cablePos);
            if (!(cableState.getBlock() instanceof CableBlock cable)) {
                continue;
            }
            if (tier != null && cable.getTier() != tier) {
                continue;
            }
            // Devices connect through any face of a neighbouring piece; skip the side
            // when any of its face nodes was already reached from an earlier side.
            ElectricNetwork.Node anchor = null;
            boolean known = false;
            for (Direction face : Direction.values()) {
                if (!cable.hasFace(cableState, face)) {
                    continue;
                }
                ElectricNetwork.Node node = new ElectricNetwork.Node(cablePos.immutable(), face);
                if (anchor == null) {
                    anchor = node;
                }
                for (ElectricNetwork.Analysis existing : result) {
                    if (existing.pieces().contains(node)) {
                        known = true;
                        break;
                    }
                }
                if (known) {
                    break;
                }
            }
            if (!known && anchor != null) {
                result.add(ElectricNetwork.analyze(level, anchor));
            }
        }
        return result;
    }

    /**
     * Networks at the two terminals of a switch. Like in the original ELN only a cable
     * on the same surface that runs into a terminal counts.
     */
    private static List<ElectricNetwork.Analysis> switchNetworks(Level level, BlockPos switchPos, BlockState switchState) {
        List<ElectricNetwork.Analysis> result = new ArrayList<>();
        Direction mountFace = SwitchBlock.mountFace(switchState);
        for (Direction side : ((SwitchBlock) switchState.getBlock()).terminalSides(switchState)) {
            BlockPos cablePos = switchPos.relative(side);
            BlockState cableState = level.getBlockState(cablePos);
            if (!(cableState.getBlock() instanceof CableBlock cable) || !cable.hasFace(cableState, mountFace)) {
                continue;
            }
            ElectricNetwork.Node node = new ElectricNetwork.Node(cablePos.immutable(), mountFace);
            boolean known = false;
            for (ElectricNetwork.Analysis existing : result) {
                if (existing.pieces().contains(node)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                result.add(ElectricNetwork.analyze(level, node));
            }
        }
        return result;
    }

    private static double sumCurrent(List<ElectricNetwork.Analysis> networks) {
        double sum = 0.0D;
        for (ElectricNetwork.Analysis analysis : networks) {
            sum += analysis.current();
        }
        return sum;
    }

    /** Rough ambient temperature in degrees Celsius from the biome (20 C in plains). */
    public static double ambientTemperature(Level level, BlockPos pos) {
        double biome = Math.max(-0.5D, Math.min(1.5D, level.getBiome(pos).value().getBaseTemperature()));
        return -10.0D + 37.5D * biome;
    }
}
