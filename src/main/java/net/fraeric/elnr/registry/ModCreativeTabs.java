package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.machine.StorageBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ELNReborn.MODID);

    private static RegistryObject<CreativeModeTab> tab(String id, Supplier<ItemStack> icon,
                                                       CreativeModeTab.DisplayItemsGenerator generator) {
        return CREATIVE_MODE_TABS.register(id, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + ELNReborn.MODID + "." + id))
                .icon(icon)
                .displayItems(generator)
                .build());
    }

    /** Empty item plus the same item fully charged. */
    private static void storage(CreativeModeTab.Output output, List<RegistryObject<Block>> blocks,
                                List<RegistryObject<Item>> items) {
        for (RegistryObject<Item> item : items) {
            output.accept(item.get());
        }
        for (int i = 0; i < items.size(); i++) {
            if (blocks.get(i).get() instanceof StorageBlock block) {
                ItemStack full = new ItemStack(items.get(i).get());
                CompoundTag tag = new CompoundTag();
                tag.putDouble("Energy", block.maxEnergy());
                full.addTagElement("BlockEntityTag", tag);
                output.accept(full);
            }
        }
    }

    public static final RegistryObject<CreativeModeTab> CABLES_TAB = tab("cables",
            () -> new ItemStack(ModBlocks.CABLE_LV.get()), (parameters, output) -> {
                ModBlocks.CABLES.forEach(cable -> output.accept(cable.get()));
                ModBlocks.STANDS.forEach(stand -> output.accept(stand.get()));
                ModItems.OVERHEAD_LINES.forEach(line -> output.accept(line.get()));
            });

    public static final RegistryObject<CreativeModeTab> SOURCES_TAB = tab("sources",
            () -> new ItemStack(ModMachines.GENERATOR_ITEM.get()), (parameters, output) -> {
                output.accept(ModBlocks.VOLTAGE_SOURCE.get());
                output.accept(ModMachines.GENERATOR_ITEM.get());
                output.accept(ModMachines.FE_CONVERTER_ITEM.get());
            });

    public static final RegistryObject<CreativeModeTab> CAPACITORS_TAB = tab("capacitors",
            () -> new ItemStack(ModMachines.CAPACITOR_ITEMS.get(4).get()),
            (parameters, output) -> storage(output, ModMachines.CAPACITORS, ModMachines.CAPACITOR_ITEMS));

    public static final RegistryObject<CreativeModeTab> BATTERIES_TAB = tab("batteries",
            () -> new ItemStack(ModMachines.BATTERY_ITEMS.get(4).get()),
            (parameters, output) -> storage(output, ModMachines.BATTERIES, ModMachines.BATTERY_ITEMS));

    public static final RegistryObject<CreativeModeTab> SWITCHING_TAB = tab("switching",
            () -> new ItemStack(ModBlocks.LIGHT_SWITCH.get()), (parameters, output) -> {
                output.accept(ModBlocks.LIGHT_SWITCH.get());
                output.accept(ModBlocks.SWITCH.get());
                output.accept(ModBlocks.BIG_SWITCH.get());
                output.accept(ModBlocks.KNIFE_SWITCH.get());
                output.accept(ModBlocks.BUTTON.get());
                output.accept(ModBlocks.TIMER.get());
                output.accept(ModWireless.WIRELESS_SWITCH_ITEM.get());
                output.accept(ModWireless.REMOTE.get());
                output.accept(ModWireless.TRANSMITTER_ITEM.get());
                output.accept(ModWireless.REDSTONE_SOURCE_ITEM.get());
                ModBlocks.RELAYS.forEach(relay -> output.accept(relay.get()));
                ModItems.RELAY_COILS.forEach(coil -> output.accept(coil.get()));
                output.accept(ModItems.RELAY_COIL_BURNT.get());
                ModBlocks.RELAYS.forEach(relay -> ModItems.RELAY_COILS.forEach(coil -> {
                    ItemStack stack = new ItemStack(relay.get());
                    CompoundTag entityTag = new CompoundTag();
                    entityTag.put("Coil", new ItemStack(coil.get()).save(new CompoundTag()));
                    stack.addTagElement("BlockEntityTag", entityTag);
                    output.accept(stack);
                }));
            });

    public static final RegistryObject<CreativeModeTab> MACHINES_TAB = tab("machines",
            () -> new ItemStack(ModMachines.MINER_ITEM.get()), (parameters, output) -> {
                output.accept(ModMachines.MINER_ITEM.get());
                output.accept(ModMachines.FURNACE_ITEM.get());
                output.accept(ModWireless.C4_ITEM.get());
                ModItems.BULBS.forEach(bulb -> output.accept(bulb.get()));
                output.accept(ModBlocks.SOCKET.get());
                output.accept(ModBlocks.STREET_LAMP.get());
                output.accept(ModBlocks.DOORBELL.get());
                output.accept(ModBlocks.BELL.get());
                output.accept(ModBlocks.SCHOOL_BELL.get());
                output.accept(ModBlocks.CLOCK_BELL.get());
                output.accept(ModBlocks.SIREN.get());
                output.accept(ModBlocks.ALARM.get());
            });

    public static final RegistryObject<CreativeModeTab> TOOLS_TAB = tab("tools",
            () -> new ItemStack(ModItems.MULTIMETER.get()), (parameters, output) -> {
                output.accept(ModItems.MULTIMETER.get());
                output.accept(ModItems.THERMOMETER.get());
                output.accept(ModItems.EXAMPLE_ITEM.get());
                output.accept(ModBlocks.EXAMPLE_BLOCK.get());
            });

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
