package net.fraeric.elnr.registry;

import net.fraeric.elnr.common.machine.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Registration of the machines (miner, furnace, generator, FE converter) and of the capacitors / batteries. */
public final class ModMachines {

    public static final double[] VOLTAGES = {50.0D, 200.0D, 800.0D};
    public static final double[] FARADS = {0.5D, 2.0D, 8.0D};
    public static final String[] FARAD_IDS = {"500mf", "2f", "8f"};
    public static final double[] AMP_HOURS = {1.0D, 4.0D, 16.0D};

    public static final List<RegistryObject<Block>> CAPACITORS = new ArrayList<>();
    public static final List<RegistryObject<Item>> CAPACITOR_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Block>> BATTERIES = new ArrayList<>();
    public static final List<RegistryObject<Item>> BATTERY_ITEMS = new ArrayList<>();

    private static BlockBehaviour.Properties metal(float hardness) {
        return BlockBehaviour.Properties.of().strength(hardness, 6.0F).sound(SoundType.METAL);
    }

    static {
        for (double v : VOLTAGES) {
            for (int i = 0; i < 3; i++) {
                register("capacitor_" + (int) v + "v_" + FARAD_IDS[i], false, v, FARADS[i]);
            }
        }
        for (double v : VOLTAGES) {
            for (int i = 0; i < 3; i++) {
                register("battery_" + (int) v + "v_" + (int) AMP_HOURS[i] + "ah", true, v, AMP_HOURS[i]);
            }
        }
    }

    private static void register(String id, boolean battery, double voltage, double capacity) {
        RegistryObject<Block> block = ModBlocks.BLOCKS.register(id,
                () -> new StorageBlock(metal(2.0F), battery, voltage, capacity));
        RegistryObject<Item> item = ModItems.ITEMS.register(id,
                () -> new StorageBlockItem((StorageBlock) block.get(), new Item.Properties()));
        (battery ? BATTERIES : CAPACITORS).add(block);
        (battery ? BATTERY_ITEMS : CAPACITOR_ITEMS).add(item);
    }

    public static final RegistryObject<Block> MINER = ModBlocks.BLOCKS.register("electric_miner",
            () -> new MinerBlock(metal(3.0F)));
    public static final RegistryObject<Item> MINER_ITEM = ModItems.ITEMS.register("electric_miner",
            () -> new net.fraeric.elnr.common.item.TooltipBlockItem(MINER.get(), new Item.Properties(),
                    "tooltip.elnr.miner.1", "tooltip.elnr.miner.2"));
    public static final RegistryObject<Block> FURNACE = ModBlocks.BLOCKS.register("electric_furnace",
            () -> new FurnaceBlock(metal(3.0F)));
    public static final RegistryObject<Item> FURNACE_ITEM = ModItems.ITEMS.register("electric_furnace",
            () -> new net.fraeric.elnr.common.item.TooltipBlockItem(FURNACE.get(), new Item.Properties(),
                    "tooltip.elnr.furnace.1", "tooltip.elnr.furnace.2"));
    public static final RegistryObject<Block> GENERATOR = ModBlocks.BLOCKS.register("generator",
            () -> new GeneratorBlock(metal(3.0F)));
    public static final RegistryObject<Item> GENERATOR_ITEM = ModItems.ITEMS.register("generator",
            () -> new net.fraeric.elnr.common.item.TooltipBlockItem(GENERATOR.get(), new Item.Properties(),
                    "tooltip.elnr.generator.1", "tooltip.elnr.generator.2"));
    public static final RegistryObject<Block> WIND_TURBINE = ModBlocks.BLOCKS.register("wind_turbine",
            () -> new WindTurbineBlock(metal(3.0F)));
    public static final RegistryObject<Item> WIND_TURBINE_ITEM = ModItems.ITEMS.register("wind_turbine",
            () -> new net.fraeric.elnr.common.item.TooltipBlockItem(WIND_TURBINE.get(), new Item.Properties(),
                    "tooltip.elnr.wind_turbine.1", "tooltip.elnr.wind_turbine.2"));
    public static final RegistryObject<Block> WIND_TURBINE_POLE = ModBlocks.BLOCKS.register("wind_turbine_pole",
            () -> new WindTurbinePoleBlock(metal(2.0F)));
    public static final RegistryObject<Item> WIND_TURBINE_POLE_ITEM = ModItems.ITEMS.register("wind_turbine_pole",
            () -> new net.fraeric.elnr.common.item.TooltipBlockItem(WIND_TURBINE_POLE.get(), new Item.Properties(),
                    "tooltip.elnr.wind_turbine_pole.1", "tooltip.elnr.wind_turbine_pole.2"));
    public static final RegistryObject<Block> FE_CONVERTER = ModBlocks.BLOCKS.register("fe_converter",
            () -> new FeConverterBlock(metal(3.0F)));
    public static final RegistryObject<Item> FE_CONVERTER_ITEM = ModItems.ITEMS.register("fe_converter",
            () -> new net.fraeric.elnr.common.item.TooltipBlockItem(FE_CONVERTER.get(), new Item.Properties(),
                    "tooltip.elnr.fe.1", "tooltip.elnr.fe.2"));

    public static final RegistryObject<BlockEntityType<StorageBlockEntity>> STORAGE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("storage", () -> BlockEntityType.Builder.of(StorageBlockEntity::new,
                    java.util.stream.Stream.concat(CAPACITORS.stream(), BATTERIES.stream())
                            .map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<MinerBlockEntity>> MINER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("electric_miner",
                    () -> BlockEntityType.Builder.of(MinerBlockEntity::new, MINER.get()).build(null));
    public static final RegistryObject<BlockEntityType<FurnaceBlockEntity>> FURNACE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("electric_furnace",
                    () -> BlockEntityType.Builder.of(FurnaceBlockEntity::new, FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("generator",
                    () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, GENERATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("wind_turbine",
                    () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, WIND_TURBINE.get()).build(null));
    public static final RegistryObject<BlockEntityType<WindTurbinePoleBlockEntity>> WIND_TURBINE_POLE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("wind_turbine_pole",
                    () -> BlockEntityType.Builder.of(WindTurbinePoleBlockEntity::new, WIND_TURBINE_POLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<FeConverterBlockEntity>> FE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("fe_converter",
                    () -> BlockEntityType.Builder.of(FeConverterBlockEntity::new, FE_CONVERTER.get()).build(null));

    public static final RegistryObject<MenuType<GeneratorMenu>> GENERATOR_MENU = ModMenus.MENUS.register("generator",
            () -> IForgeMenuType.create((id, inv, buf) -> new GeneratorMenu(id, inv, buf.readBlockPos())));
    public static final RegistryObject<MenuType<FurnaceMenu>> FURNACE_MENU = ModMenus.MENUS.register("electric_furnace",
            () -> IForgeMenuType.create((id, inv, buf) -> new FurnaceMenu(id, inv, buf.readBlockPos())));
    public static final RegistryObject<MenuType<FeConverterMenu>> FE_MENU = ModMenus.MENUS.register("fe_converter",
            () -> IForgeMenuType.create((id, inv, buf) -> new FeConverterMenu(id, inv, buf.readBlockPos())));

    private ModMachines() {
    }

    /** Forces the class to load, so everything above is registered before the registry events. */
    public static void init() {
    }
}
