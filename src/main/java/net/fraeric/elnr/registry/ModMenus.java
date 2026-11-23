package net.fraeric.elnr.registry;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.electric.SpeakerMenu;
import net.fraeric.elnr.common.electric.TimerMenu;
import net.fraeric.elnr.common.electric.VoltageSourceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ELNReborn.MODID);

    public static final RegistryObject<MenuType<VoltageSourceMenu>> VOLTAGE_SOURCE =
            MENUS.register("voltage_source", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new VoltageSourceMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<SpeakerMenu>> SPEAKER =
            MENUS.register("speaker", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new SpeakerMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<TimerMenu>> TIMER =
            MENUS.register("timer", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new TimerMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<net.fraeric.elnr.common.electric.RelayMenu>> RELAY =
            MENUS.register("relay", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new net.fraeric.elnr.common.electric.RelayMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<net.fraeric.elnr.common.electric.LimiterMenu>> LIMITER =
            MENUS.register("limiter", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new net.fraeric.elnr.common.electric.LimiterMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<net.fraeric.elnr.common.electric.SocketMenu>> SOCKET =
            MENUS.register("socket", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new net.fraeric.elnr.common.electric.SocketMenu(windowId, inventory, data.readBlockPos())));
    public static final RegistryObject<MenuType<net.fraeric.elnr.common.electric.StreetLampMenu>> STREET_LAMP =
            MENUS.register("street_lamp", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new net.fraeric.elnr.common.electric.StreetLampMenu(windowId, inventory, data.readBlockPos())));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
