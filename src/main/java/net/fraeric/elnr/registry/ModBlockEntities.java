package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.blockentity.ExampleBlockEntity;
import net.fraeric.elnr.common.cable.CableBlockEntity;
import net.fraeric.elnr.common.cable.StandBlockEntity;
import net.fraeric.elnr.common.electric.SocketBlockEntity;
import net.fraeric.elnr.common.electric.SpeakerBlockEntity;
import net.fraeric.elnr.common.electric.TimerBlockEntity;
import net.fraeric.elnr.common.electric.StreetLampBlockEntity;
import net.fraeric.elnr.common.electric.VoltageSourceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ELNReborn.MODID);

    // Example block entity, valid for ModBlocks.EXAMPLE_BLOCK
    public static final RegistryObject<BlockEntityType<ExampleBlockEntity>> EXAMPLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("example_block_entity",
                    () -> BlockEntityType.Builder.of(ExampleBlockEntity::new, ModBlocks.EXAMPLE_BLOCK.get()).build(null));

    // Shared by all four cable tiers
    public static final RegistryObject<BlockEntityType<CableBlockEntity>> CABLE =
            BLOCK_ENTITIES.register("cable",
                    () -> BlockEntityType.Builder.of(CableBlockEntity::new,
                            ModBlocks.CABLES.stream().map(RegistryObject::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    // Shared by all cable stands
    public static final RegistryObject<BlockEntityType<StandBlockEntity>> STAND =
            BLOCK_ENTITIES.register("stand",
                    () -> BlockEntityType.Builder.of(StandBlockEntity::new,
                            ModBlocks.STANDS.stream().map(RegistryObject::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    public static final RegistryObject<BlockEntityType<SocketBlockEntity>> SOCKET =
            BLOCK_ENTITIES.register("socket",
                    () -> BlockEntityType.Builder.of(SocketBlockEntity::new, ModBlocks.SOCKET.get()).build(null));

    public static final RegistryObject<BlockEntityType<VoltageSourceBlockEntity>> VOLTAGE_SOURCE =
            BLOCK_ENTITIES.register("voltage_source",
                    () -> BlockEntityType.Builder.of(VoltageSourceBlockEntity::new, ModBlocks.VOLTAGE_SOURCE.get()).build(null));

    public static final RegistryObject<BlockEntityType<StreetLampBlockEntity>> STREET_LAMP =
            BLOCK_ENTITIES.register("street_lamp",
                    () -> BlockEntityType.Builder.of(StreetLampBlockEntity::new, ModBlocks.STREET_LAMP.get(), ModBlocks.WALL_STREET_LAMP.get(), ModBlocks.CHANDELIER.get()).build(null));

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker",
                    () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new,
                            ModBlocks.DOORBELL.get(), ModBlocks.SIREN.get(), ModBlocks.SCHOOL_BELL.get(),
                            ModBlocks.CLOCK_BELL.get(), ModBlocks.BELL.get(), ModBlocks.ALARM.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.fraeric.elnr.common.electric.MeterBlockEntity>> METER =
            BLOCK_ENTITIES.register("meter",
                    () -> BlockEntityType.Builder.of(net.fraeric.elnr.common.electric.MeterBlockEntity::new,
                            ModBlocks.VOLTMETER.get(), ModBlocks.AMMETER.get(), ModBlocks.WATTMETER.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.fraeric.elnr.common.electric.LimiterBlockEntity>> LIMITER =
            BLOCK_ENTITIES.register("limiter",
                    () -> BlockEntityType.Builder.of(net.fraeric.elnr.common.electric.LimiterBlockEntity::new,
                            ModBlocks.LIMITER.get()).build(null));

    public static final RegistryObject<BlockEntityType<TimerBlockEntity>> TIMER =
            BLOCK_ENTITIES.register("timer",
                    () -> BlockEntityType.Builder.of(TimerBlockEntity::new, ModBlocks.TIMER.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.fraeric.elnr.common.electric.RelayBlockEntity>> RELAY =
            BLOCK_ENTITIES.register("relay",
                    () -> BlockEntityType.Builder.of(net.fraeric.elnr.common.electric.RelayBlockEntity::new,
                            ModBlocks.RELAYS.stream().map(RegistryObject::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
