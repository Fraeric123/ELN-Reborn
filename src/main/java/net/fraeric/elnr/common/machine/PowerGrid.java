package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.cable.StandBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Distributes power between the machines of one electric network. Runs every second
 * tick at the end of the level tick.
 */
@Mod.EventBusSubscriber(modid = ELNReborn.MODID)
public final class PowerGrid {

    private static final int INTERVAL = 2;
    public static final double DT = INTERVAL * 0.05D;
    private static final Map<ServerLevel, Set<ElectricMachineBlockEntity>> MACHINES = new HashMap<>();

    private PowerGrid() {
    }

    static void add(ElectricMachineBlockEntity be) {
        if (be.getLevel() instanceof ServerLevel level) {
            MACHINES.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(be);
        }
    }

    static void remove(ElectricMachineBlockEntity be) {
        if (be.getLevel() instanceof ServerLevel level) {
            Set<ElectricMachineBlockEntity> set = MACHINES.get(level);
            if (set != null) {
                set.remove(be);
            }
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MACHINES.remove(level);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        Set<ElectricMachineBlockEntity> set = MACHINES.get(level);
        if (set == null || set.isEmpty()) {
            return;
        }
        List<ElectricMachineBlockEntity> all = new ArrayList<>(set);
        Set<ElectricMachineBlockEntity> done = new HashSet<>();
        for (ElectricMachineBlockEntity m : all) {
            if (done.contains(m) || m.isRemoved()) {
                continue;
            }
            ElectricNetwork.Analysis analysis = ElectricNetwork.analyzeMachine(level, m.getBlockPos());
            List<ElectricMachineBlockEntity> group = new ArrayList<>();
            if (analysis != null) {
                for (BlockPos p : analysis.machines()) {
                    if (level.getBlockEntity(p) instanceof ElectricMachineBlockEntity o && !o.isRemoved()) {
                        group.add(o);
                    }
                }
            }
            if (!group.contains(m)) {
                group.add(m);
            }
            done.addAll(group);
            step(level, group, analysis);
        }
    }

    private static void step(ServerLevel level, List<ElectricMachineBlockEntity> group,
                             ElectricNetwork.Analysis analysis) {
        final double dt = DT;
        double vIdeal = 0.0D;
        double minRatedCurrent = Double.MAX_VALUE;
        double lampW = 0.0D;
        if (analysis != null) {
            for (BlockPos p : analysis.sources()) {
                if (level.getBlockEntity(p) instanceof VoltageSourceBlockEntity s) {
                    vIdeal = Math.max(vIdeal, Math.abs(s.getVoltage()));
                }
            }
            for (ElectricNetwork.Node node : analysis.pieces()) {
                BlockState st = level.getBlockState(node.pos());
                if (st.getBlock() instanceof StandBlock stand) {
                    minRatedCurrent = Math.min(minRatedCurrent, stand.getTier().getMaxCurrent());
                } else if (st.getBlock() instanceof CableBlock cable) {
                    minRatedCurrent = Math.min(minRatedCurrent, cable.getTier().getMaxCurrent());
                }
            }
            lampW = analysis.current() * Math.abs(analysis.voltage());
        }
        double vFixed = vIdeal;
        for (ElectricMachineBlockEntity m : group) {
            vFixed = Math.max(vFixed, m.fixedSourceVoltage());
        }
        double v = vFixed;
        if (v <= 0.0D) {
            for (ElectricMachineBlockEntity m : group) {
                v = Math.max(v, m.storageVoltage());
            }
        }

        // Overvoltage destroys the machine, like bulbs and switches.
        for (ElectricMachineBlockEntity m : group) {
            double rated = m.ratedVoltage();
            if (rated > 0 && v > rated * 1.3D) {
                BlockPos p = m.getBlockPos();
                level.removeBlock(p, false);
                level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                        8, 0.25D, 0.25D, 0.25D, 0.02D);
                level.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                        3, 0.15D, 0.15D, 0.15D, 0.01D);
                ElectricNetwork.updateAt(level, p);
                return;
            }
        }

        double demandW = lampW;
        double genCap = 0.0D;
        double dischCap = 0.0D;
        double chargeCap = 0.0D;
        for (ElectricMachineBlockEntity m : group) {
            if (m.isStorage()) {
                if (v > 0 && m.storageVoltage() >= 0.9D * v) {
                    dischCap += m.dischargeMaxW(dt);
                }
                chargeCap += m.chargeRequestW(v, dt);
            } else {
                demandW += m.demandWatts(v);
                if (v > 0 && m.fixedSourceVoltage() >= 0.9D * v) {
                    genCap += m.sourceMaxWatts();
                }
            }
        }
        boolean ideal = v > 0 && vIdeal >= 0.9D * v;
        double headroomW = minRatedCurrent == Double.MAX_VALUE ? Double.MAX_VALUE
                : Math.max(0.0D, minRatedCurrent * v - demandW);
        double chargeW = vFixed > 0 ? Math.min(chargeCap, headroomW) : 0.0D;

        double sat = 1.0D;
        double genOut = 0.0D;
        double discharge = 0.0D;
        double charge = 0.0D;
        if (v <= 0) {
            sat = 0.0D;
        } else if (ideal) {
            charge = chargeW;
        } else {
            double gd = Math.min(demandW, genCap);
            discharge = Math.min(demandW - gd, dischCap);
            sat = demandW > 0 ? (gd + discharge) / demandW : 1.0D;
            charge = Math.min(chargeW, genCap - gd);
            genOut = gd + charge;
        }

        double consumedW = 0.0D;
        for (ElectricMachineBlockEntity m : group) {
            if (m.isStorage()) {
                double w = 0.0D;
                if (v > 0 && discharge > 0 && dischCap > 0 && m.storageVoltage() >= 0.9D * v) {
                    w -= discharge * m.dischargeMaxW(dt) / dischCap;
                }
                if (charge > 0 && chargeCap > 0) {
                    w += charge * m.chargeRequestW(v, dt) / chargeCap;
                }
                m.applyPower(v, w, dt);
                if (w > 0) {
                    consumedW += w;
                }
            } else if (v > 0 && genCap > 0 && m.fixedSourceVoltage() >= 0.9D * v && m.sourceMaxWatts() > 0) {
                m.applyPower(v, -genOut * m.sourceMaxWatts() / genCap, dt);
            } else {
                double w = m.demandWatts(v) * sat;
                m.applyPower(v, w, dt);
                consumedW += w;
            }
        }

        ElectricMachineBlockEntity leader = group.get(0);
        long sig = Math.round(v) * 1_000_003L + Math.round((consumedW + lampW * sat) / Math.max(v, 1.0D) * 4.0D) * 31L
                + (sat > 0 ? 1 : 0);
        if (sig != leader.lastSig) {
            leader.lastSig = sig;
            ElectricNetwork.updateAt(level, leader.getBlockPos());
        }
    }
}
