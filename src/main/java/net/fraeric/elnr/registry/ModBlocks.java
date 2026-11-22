package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.block.ExampleBlock;
import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.CableTier;
import net.fraeric.elnr.common.cable.StandBlock;
import net.fraeric.elnr.common.electric.ButtonBlock;
import net.fraeric.elnr.common.electric.SelectorSwitchBlock;
import net.fraeric.elnr.common.electric.KnifeSwitchBlock;
import net.fraeric.elnr.common.electric.SocketBlock;
import net.fraeric.elnr.common.electric.SpeakerBlock;
import net.fraeric.elnr.common.electric.SpeakerKind;
import net.fraeric.elnr.common.electric.TimerBlock;
import net.fraeric.elnr.common.electric.StreetLampBlock;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.fraeric.elnr.common.electric.VoltageSourceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fraeric.elnr.common.cable.CableColors;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ELNReborn.MODID);

    // Example block (with a block entity, see ModBlockEntities)
    public static final RegistryObject<Block> EXAMPLE_BLOCK =
            registerBlock("example_block",
                    () -> new ExampleBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(2.0F, 6.0F)));

    // Cables (bare, 16 insulated colours and a bundled cable per tier)
    public static final List<RegistryObject<Block>> CABLES = new ArrayList<>();
    /** Cable stands: one per tier and cable type (bare, 16 colours, bundled). */
    public static final List<RegistryObject<Block>> STANDS = new ArrayList<>();
    /** Relay bases: {rated voltage, rated current}. A base is a separate block per rating. */
    public static final double[][] RELAY_BASES = {{30, 5}, {60, 20}, {300, 15}, {1600, 5}};
    public static final List<RegistryObject<Block>> RELAYS = new ArrayList<>();

    /** Rating of the big selector switch. */
    public static final double BIG_SWITCH_VOLTAGE = 1600.0D;
    public static final double BIG_SWITCH_CURRENT = 5.0D;
    public static final RegistryObject<Block> CABLE_LV = registerCable("cable_lv", CableTier.LV);
    public static final RegistryObject<Block> CABLE_MV = registerCable("cable_mv", CableTier.MV);
    public static final RegistryObject<Block> CABLE_HV = registerCable("cable_hv", CableTier.HV);
    public static final RegistryObject<Block> CABLE_VHV = registerCable("cable_vhv", CableTier.VHV);

    // Electrical machines
    public static final RegistryObject<Block> SOCKET = registerSocket();
    public static final RegistryObject<Block> VOLTAGE_SOURCE =
            registerBlock("voltage_source", () -> new VoltageSourceBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.voltage_source.1", "tooltip.elnr.voltage_source.2");
    public static final RegistryObject<Block> LIGHT_SWITCH =
            registerBlock("light_switch", () -> new SwitchBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.light_switch.1", "tooltip.elnr.light_switch.2", "tooltip.elnr.light_switch.3", "tooltip.elnr.light_switch.4");
    public static final RegistryObject<Block> SWITCH =
            registerBlock("switch", () -> new SelectorSwitchBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.switch.1", "tooltip.elnr.switch.2", "tooltip.elnr.switch.3", "tooltip.elnr.switch.4");
    /** Big selector switch: bigger case and contacts, 1600 V / 5 A. */
    public static final RegistryObject<Block> BIG_SWITCH =
            registerBlock("big_switch", () -> new SelectorSwitchBlock(
                    BlockBehaviour.Properties.of().strength(2.5F, 6.0F).sound(SoundType.METAL).noOcclusion(),
                    true, BIG_SWITCH_VOLTAGE, BIG_SWITCH_CURRENT),
                    "tooltip.elnr.big_switch.1", "tooltip.elnr.big_switch.2", "tooltip.elnr.big_switch.3");
    /** Knife switch: plain on/off high voltage switch with a big lever. */
    public static final RegistryObject<Block> KNIFE_SWITCH =
            registerBlock("knife_switch", () -> new KnifeSwitchBlock(
                    BlockBehaviour.Properties.of().strength(2.5F, 6.0F).sound(SoundType.METAL).noOcclusion(),
                    BIG_SWITCH_VOLTAGE, BIG_SWITCH_CURRENT),
                    "tooltip.elnr.knife_switch.1", "tooltip.elnr.knife_switch.2", "tooltip.elnr.knife_switch.3");
    public static final RegistryObject<Block> BUTTON =
            registerBlock("button", () -> new ButtonBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.button.1", "tooltip.elnr.button.2", "tooltip.elnr.button.3");
    public static final RegistryObject<Block> DOORBELL =
            registerBlock("doorbell", () -> new SpeakerBlock(SpeakerKind.DOORBELL,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.speaker.1", "tooltip.elnr.speaker.2");
    public static final RegistryObject<Block> SIREN =
            registerBlock("siren", () -> new SpeakerBlock(SpeakerKind.SIREN,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.speaker.1", "tooltip.elnr.speaker.2");
    public static final RegistryObject<Block> SCHOOL_BELL =
            registerBlock("school_bell", () -> new SpeakerBlock(SpeakerKind.SCHOOL_BELL,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.speaker.1", "tooltip.elnr.speaker.2");
    public static final RegistryObject<Block> CLOCK_BELL =
            registerBlock("clock_bell", () -> new SpeakerBlock(SpeakerKind.CLOCK_BELL,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.speaker.1", "tooltip.elnr.speaker.2");
    public static final RegistryObject<Block> BELL =
            registerBlock("bell", () -> new SpeakerBlock(SpeakerKind.BELL,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.speaker.1", "tooltip.elnr.speaker.2");
    public static final RegistryObject<Block> ALARM =
            registerBlock("alarm", () -> new SpeakerBlock(SpeakerKind.ALARM,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.speaker.1", "tooltip.elnr.speaker.2");
    public static final RegistryObject<Block> VOLTMETER =
            registerBlock("voltmeter", () -> new net.fraeric.elnr.common.electric.MeterBlock(
                    net.fraeric.elnr.common.electric.MeterBlock.Kind.VOLTAGE,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.meter.1", "tooltip.elnr.meter.2");
    public static final RegistryObject<Block> AMMETER =
            registerBlock("ammeter", () -> new net.fraeric.elnr.common.electric.MeterBlock(
                    net.fraeric.elnr.common.electric.MeterBlock.Kind.CURRENT,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.meter.1", "tooltip.elnr.meter.2");
    public static final RegistryObject<Block> WATTMETER =
            registerBlock("wattmeter", () -> new net.fraeric.elnr.common.electric.MeterBlock(
                    net.fraeric.elnr.common.electric.MeterBlock.Kind.POWER,
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.meter.1", "tooltip.elnr.meter.2");
    public static final RegistryObject<Block> LIMITER =
            registerBlock("limiter", () -> new net.fraeric.elnr.common.electric.LimiterBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion(),
                    1600.0D, 20.0D),
                    "tooltip.elnr.limiter.1", "tooltip.elnr.limiter.2", "tooltip.elnr.limiter.3");
    public static final RegistryObject<Block> REDSTONE_SWITCH =
            registerBlock("redstone_switch", () -> new net.fraeric.elnr.common.electric.RedstoneSwitchBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.redstone_switch.1", "tooltip.elnr.redstone_switch.2");
    public static final RegistryObject<Block> TIMER =
            registerBlock("timer", () -> new TimerBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.timer.1", "tooltip.elnr.timer.2", "tooltip.elnr.timer.3");
    public static final RegistryObject<Block> STREET_LAMP =
            registerBlock("street_lamp", () -> new StreetLampBlock(
                    BlockBehaviour.Properties.of().strength(3.0F, 8.0F).sound(SoundType.METAL).noOcclusion()),
                    "tooltip.elnr.street_lamp.1", "tooltip.elnr.street_lamp.2");

    private static RegistryObject<Block> registerSocket() {
        RegistryObject<Block> registered = BLOCKS.register("socket", () -> new SocketBlock(
                BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
        ModItems.registerSocketItem("socket", registered,
                "tooltip.elnr.socket.1", "tooltip.elnr.socket.2", "tooltip.elnr.socket.3");
        return registered;
    }

    /** @return the cable block of the given type; used by stands to act as that cable. */
    public static CableBlock findCable(CableTier tier, int color, boolean bundled) {
        for (RegistryObject<Block> registered : CABLES) {
            if (registered.get() instanceof CableBlock cable && cable.getTier() == tier
                    && cable.getColor() == color && cable.isBundled() == bundled) {
                return cable;
            }
        }
        throw new IllegalStateException("No cable block for " + tier + " " + color + " " + bundled);
    }

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        ModItems.registerBlockItem(name, registered);
        return registered;
    }

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> block, String... tooltipKeys) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        ModItems.registerBlockItem(name, registered, tooltipKeys);
        return registered;
    }

    static {
        for (CableTier tier : CableTier.values()) {
            String base = "cable_" + tier.name().toLowerCase(java.util.Locale.ROOT);
            for (int color = 0; color < CableColors.COUNT; color++) {
                registerCable(base + "_" + CableColors.NAMES[color], tier, color, false);
            }
            registerCable(base + "_bundled", tier, -1, true);
        }
    }

    static {
        for (CableTier tier : CableTier.values()) {
            String base = "stand_" + tier.name().toLowerCase(java.util.Locale.ROOT);
            registerStand(base, tier, -1, false);
            for (int color = 0; color < CableColors.COUNT; color++) {
                registerStand(base + "_" + CableColors.NAMES[color], tier, color, false);
            }
            registerStand(base + "_bundled", tier, -1, true);
        }
    }

    static {
        for (double[] base : RELAY_BASES) {
            String name = "relay_" + (int) base[0] + "v_" + (int) base[1] + "a";
            RegistryObject<Block> registered = BLOCKS.register(name, () -> new net.fraeric.elnr.common.electric.RelayBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion(),
                    base[0], base[1]));
            ModItems.registerRelayItem(name, registered);
            RELAYS.add(registered);
        }
    }

    private static void registerStand(String name, CableTier tier, int color, boolean bundled) {
        RegistryObject<Block> registered = BLOCKS.register(name, () -> new StandBlock(tier, color, bundled,
                BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
        ModItems.registerStandItem(name, registered);
        STANDS.add(registered);
    }

    private static RegistryObject<Block> registerCable(String name, CableTier tier) {
        return registerCable(name, tier, -1, false);
    }

    private static RegistryObject<Block> registerCable(String name, CableTier tier, int color, boolean bundled) {
        RegistryObject<Block> registered = BLOCKS.register(name, () -> new CableBlock(tier, color, bundled,
                BlockBehaviour.Properties.of()
                        .noCollission()
                        .noOcclusion()
                        .strength(0.4F)
                        .sound(SoundType.WOOL)));
        ModItems.registerCableItem(name, registered);
        CABLES.add(registered);
        return registered;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
