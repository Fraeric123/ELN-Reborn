package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.StandBlock;
import net.fraeric.elnr.common.cable.StandItem;
import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.common.item.SocketItem;
import net.fraeric.elnr.common.item.TooltipBlockItem;
import java.util.ArrayList;
import java.util.List;
import net.fraeric.elnr.common.item.TooltipItem;
import net.fraeric.elnr.common.probe.ProbeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ELNReborn.MODID);

    // Example plain item
    public static final RegistryObject<Item> EXAMPLE_ITEM =
            ITEMS.register("example_item", () -> new Item(new Item.Properties()));

    // Bulbs: 12 / 50 / 250 V, each clear or in one of the 16 dye colours (50 W).
    // "bulb" itself is the clear 50 V bulb (the original item).
    public static final List<RegistryObject<Item>> BULBS = new ArrayList<>();
    public static final RegistryObject<Item> BULB;

    static {
        RegistryObject<Item> clear50 = null;
        Object[][] colors = {{"white", 16383998},{"orange", 16351261},{"magenta", 13061821},{"light_blue", 3847130},{"yellow", 16701501},{"lime", 8439583},{"pink", 15961002},{"gray", 8026746},{"light_gray", 12434869},{"cyan", 1481884},{"purple", 8991416},{"blue", 3949738},{"brown", 8606770},{"green", 6192150},{"red", 11546150},{"black", 5592405}};
        for (int voltage : new int[]{12, 50, 250}) {
            for (int i = -1; i < colors.length; i++) {
                String color = i < 0 ? null : (String) colors[i][0];
                int tint = i < 0 ? -1 : (Integer) colors[i][1];
                String name = voltage == 50 && color == null ? "bulb"
                        : "bulb_" + voltage + "v" + (color == null ? "" : "_" + color);
                RegistryObject<Item> bulb = ITEMS.register(name,
                        () -> new BulbItem(new Item.Properties(), voltage, tint, color));
                BULBS.add(bulb);
                if (voltage == 50 && color == null) {
                    clear50 = bulb;
                }
            }
        }
        BULB = clear50;
    }

    /** Relay coils by voltage, and the burnt coil. */
    public static final List<RegistryObject<Item>> RELAY_COILS = new java.util.ArrayList<>();
    static {
        for (int voltage : net.fraeric.elnr.common.electric.RelayCoilItem.VOLTAGES) {
            RELAY_COILS.add(ITEMS.register("relay_coil_" + voltage + "v",
                    () -> new net.fraeric.elnr.common.electric.RelayCoilItem(voltage, new Item.Properties().stacksTo(16))));
        }
    }
    public static final RegistryObject<Item> RELAY_COIL_BURNT = ITEMS.register("relay_coil_burnt",
            () -> new net.fraeric.elnr.common.electric.RelayCoilItem(0, new Item.Properties().stacksTo(16)));

    /** Registers the item of a relay base. */
    static RegistryObject<Item> registerRelayItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new net.fraeric.elnr.common.electric.RelayBlockItem(block.get(), new Item.Properties()));
    }

    /** Overhead lines: one per voltage class, single wire or bundle. */
    public static final List<RegistryObject<Item>> OVERHEAD_LINES = new java.util.ArrayList<>();
    static {
        for (net.fraeric.elnr.common.cable.CableTier tier : net.fraeric.elnr.common.cable.CableTier.values()) {
            for (boolean bundled : new boolean[]{false, true}) {
                OVERHEAD_LINES.add(ITEMS.register("overhead_line_" + tier.getId() + (bundled ? "_bundled" : ""),
                        () -> new net.fraeric.elnr.common.cable.OverheadLineItem(tier, bundled, new Item.Properties())));
            }
        }
    }

    // Multimeter: click a cable or device to read voltage, current and power
    public static final RegistryObject<Item> MULTIMETER =
            ITEMS.register("multimeter", () -> new ProbeItem(new Item.Properties().stacksTo(1),
                    ProbeItem.Mode.MULTIMETER, "tooltip.elnr.multimeter.1", "tooltip.elnr.multimeter.2"));

    // Thermometer: click a cable or device to read its temperature
    public static final RegistryObject<Item> THERMOMETER =
            ITEMS.register("thermometer", () -> new ProbeItem(new Item.Properties().stacksTo(1),
                    ProbeItem.Mode.THERMOMETER, "tooltip.elnr.thermometer.1", "tooltip.elnr.thermometer.2"));

    /**
     * Registers the block item for a block. Called from {@link ModBlocks}.
     */
    static RegistryObject<Item> registerBlockItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /**
     * Registers a block item with extra translated tooltip lines.
     */
    static RegistryObject<Item> registerBlockItem(String name, RegistryObject<? extends Block> block, String... tooltipKeys) {
        return ITEMS.register(name, () -> new TooltipBlockItem(block.get(), new Item.Properties(), tooltipKeys));
    }

    /** Registers the bulb socket item, which can also be put straight onto a cable. */
    static RegistryObject<Item> registerSocketItem(String name, RegistryObject<? extends Block> block, String... tooltipKeys) {
        return ITEMS.register(name, () -> new SocketItem(block.get(), new Item.Properties(), tooltipKeys));
    }

    /** Registers the item of a cable stand. */
    static RegistryObject<Item> registerStandItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new StandItem((StandBlock) block.get(), new Item.Properties()));
    }

    /**
     * Registers a cable item, which places cable pieces instead of a plain block.
     */
    static RegistryObject<Item> registerCableItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new CableItem((CableBlock) block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
