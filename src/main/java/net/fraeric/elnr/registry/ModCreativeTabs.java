package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ELNReborn.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup." + ELNReborn.MODID + ".main"))
                            .icon(() -> new ItemStack(ModItems.BULB.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.EXAMPLE_ITEM.get());
                                output.accept(ModBlocks.EXAMPLE_BLOCK.get());
                                ModBlocks.CABLES.forEach(cable -> output.accept(cable.get()));
                                ModItems.BULBS.forEach(bulb -> output.accept(bulb.get()));
                                ModBlocks.STANDS.forEach(stand -> output.accept(stand.get()));
                                ModItems.OVERHEAD_LINES.forEach(line -> output.accept(line.get()));
                                output.accept(ModBlocks.SOCKET.get());
                                output.accept(ModBlocks.VOLTAGE_SOURCE.get());
                                output.accept(ModBlocks.LIGHT_SWITCH.get());
                                output.accept(ModBlocks.SWITCH.get());
                                output.accept(ModBlocks.BIG_SWITCH.get());
                                output.accept(ModBlocks.KNIFE_SWITCH.get());
                                output.accept(ModBlocks.BUTTON.get());
                                output.accept(ModBlocks.TIMER.get());
                                ModBlocks.RELAYS.forEach(relay -> output.accept(relay.get()));
                                ModItems.RELAY_COILS.forEach(coil -> output.accept(coil.get()));
                                output.accept(ModItems.RELAY_COIL_BURNT.get());
                                ModBlocks.RELAYS.forEach(relay -> ModItems.RELAY_COILS.forEach(coil -> {
                                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(relay.get());
                                    net.minecraft.nbt.CompoundTag entityTag = new net.minecraft.nbt.CompoundTag();
                                    entityTag.put("Coil", new net.minecraft.world.item.ItemStack(coil.get()).save(new net.minecraft.nbt.CompoundTag()));
                                    stack.addTagElement("BlockEntityTag", entityTag);
                                    output.accept(stack);
                                }));
                                output.accept(ModBlocks.STREET_LAMP.get());
                                output.accept(ModBlocks.DOORBELL.get());
                                output.accept(ModBlocks.BELL.get());
                                output.accept(ModBlocks.SCHOOL_BELL.get());
                                output.accept(ModBlocks.CLOCK_BELL.get());
                                output.accept(ModBlocks.SIREN.get());
                                output.accept(ModBlocks.ALARM.get());
                                output.accept(ModItems.MULTIMETER.get());
                                output.accept(ModItems.THERMOMETER.get());
                            })
                            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
