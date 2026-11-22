package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ELNReborn.MODID);

    /** Length of every sound in seconds (used to repeat a sound while the signal lasts). */
    public static final Map<String, Double> DURATIONS = Map.ofEntries(
            Map.entry("alarm_critical", 1.03D),
            Map.entry("bellring_01", 0.73D),
            Map.entry("bellring_02", 1.96D),
            Map.entry("bellring_03", 2.87D),
            Map.entry("clockbellfullhour", 36.99D),
            Map.entry("doorbell_01", 3.03D),
            Map.entry("doorbell_02", 2.06D),
            Map.entry("doorbell_03", 2.22D),
            Map.entry("doorbell_04", 3.6D),
            Map.entry("doorbell_05", 10.29D),
            Map.entry("doorphonecalling_01", 4.36D),
            Map.entry("doorphonecalling_02", 1.67D),
            Map.entry("schoolbellringing", 2.61D),
            Map.entry("siren", 11.11D));

    static {
        for (String name : DURATIONS.keySet()) {
            SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ELNReborn.MODID, name)));
        }
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(ELNReborn.MODID, name);
    }

    public static SoundEvent get(String name) {
        return ForgeRegistries.SOUND_EVENTS.getValue(id(name));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
