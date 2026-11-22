package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.ELNReborn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Frequency based wireless link: powered transmitters and remote control pulses, range 50 blocks. */
@Mod.EventBusSubscriber(modid = ELNReborn.MODID)
public final class WirelessManager {

    public static final double RANGE = 50.0D;
    private static final Map<ServerLevel, Set<WirelessReceiver>> RECEIVERS = new HashMap<>();
    private static final Map<ServerLevel, Set<WirelessTransmitter>> TRANSMITTERS = new HashMap<>();

    private WirelessManager() {
    }

    public static void add(WirelessReceiver r) {
        if (r.be().getLevel() instanceof ServerLevel level) {
            RECEIVERS.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(r);
        }
    }

    public static void remove(WirelessReceiver r) {
        if (r.be().getLevel() instanceof ServerLevel level && RECEIVERS.containsKey(level)) {
            RECEIVERS.get(level).remove(r);
        }
    }

    public static void add(WirelessTransmitter t) {
        if (t.be().getLevel() instanceof ServerLevel level) {
            TRANSMITTERS.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(t);
        }
    }

    public static void remove(WirelessTransmitter t) {
        if (t.be().getLevel() instanceof ServerLevel level && TRANSMITTERS.containsKey(level)) {
            TRANSMITTERS.get(level).remove(t);
        }
    }

    /** Sends a remote control pulse; returns how many receivers got it. */
    public static int pulse(ServerLevel level, Vec3 origin, int frequency) {
        int count = 0;
        for (WirelessReceiver r : new ArrayList<>(RECEIVERS.getOrDefault(level, Set.of()))) {
            if (r.be().isRemoved() || r.getFrequency() != frequency) {
                continue;
            }
            if (origin.distanceToSqr(Vec3.atCenterOf(r.be().getBlockPos())) <= RANGE * RANGE) {
                r.pulse();
                count++;
            }
        }
        return count;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % 5 != 0) {
            return;
        }
        Set<WirelessReceiver> receivers = RECEIVERS.get(level);
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        List<WirelessTransmitter> tx = new ArrayList<>(TRANSMITTERS.getOrDefault(level, Set.of()));
        for (WirelessReceiver r : new ArrayList<>(receivers)) {
            if (r.be().isRemoved()) {
                continue;
            }
            Vec3 at = Vec3.atCenterOf(r.be().getBlockPos());
            boolean active = false;
            for (WirelessTransmitter t : tx) {
                if (!t.be().isRemoved() && t.isTransmitting() && t.getFrequency() == r.getFrequency()
                        && at.distanceToSqr(Vec3.atCenterOf(t.be().getBlockPos())) <= RANGE * RANGE) {
                    active = true;
                    break;
                }
            }
            r.setTxActive(active);
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RECEIVERS.remove(level);
            TRANSMITTERS.remove(level);
        }
    }
}
