package net.fraeric.elnr;

import com.mojang.logging.LogUtils;
import net.fraeric.elnr.client.CableBlockRenderer;
import net.fraeric.elnr.client.ExampleEntityRenderer;
import net.fraeric.elnr.client.StandBlockRenderer;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.fraeric.elnr.registry.ModBlocks;
import net.fraeric.elnr.registry.ModCreativeTabs;
import net.fraeric.elnr.registry.ModEntities;
import net.fraeric.elnr.registry.ModItems;
import net.fraeric.elnr.registry.ModMenus;
import net.fraeric.elnr.client.TimerScreen;
import net.fraeric.elnr.registry.ModSounds;
import net.fraeric.elnr.client.SpeakerScreen;
import net.fraeric.elnr.client.VoltageSourceScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ELNReborn.MODID)
public class ELNReborn
{

    public static final String MODID = "elnr";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ELNReborn(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Registries
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        net.fraeric.elnr.registry.ModMachines.init();
        net.fraeric.elnr.registry.ModWireless.init();
        ModMenus.register(modEventBus);
        ModSounds.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        net.fraeric.elnr.network.ModNetwork.register();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("HELLO from server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> {
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.VOLTAGE_SOURCE.get(), VoltageSourceScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.SPEAKER.get(), SpeakerScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.TIMER.get(), TimerScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.STREET_LAMP.get(), net.fraeric.elnr.client.StreetLampScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.LIMITER.get(), net.fraeric.elnr.client.LimiterScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.RELAY.get(), net.fraeric.elnr.client.RelayScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        net.fraeric.elnr.registry.ModMachines.GENERATOR_MENU.get(), net.fraeric.elnr.client.PanelScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        net.fraeric.elnr.registry.ModMachines.FE_MENU.get(), net.fraeric.elnr.client.PanelScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        net.fraeric.elnr.registry.ModMachines.FURNACE_MENU.get(), net.fraeric.elnr.client.FurnaceScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        net.fraeric.elnr.registry.ModWireless.FREQUENCY_MENU.get(), net.fraeric.elnr.client.FrequencyScreen::new);
                net.minecraft.client.gui.screens.MenuScreens.register(
                        net.fraeric.elnr.registry.ModWireless.REMOTE_MENU.get(), net.fraeric.elnr.client.FrequencyScreen::new);
            });
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        public static void registerBlockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event)
        {
            event.register((state, level, pos, tintIndex) -> {
                if (level != null && pos != null
                        && level.getBlockEntity(pos) instanceof net.fraeric.elnr.common.electric.SocketBlockEntity socket
                        && socket.hasBulb()) {
                    return net.fraeric.elnr.common.item.BulbItem.tintOf(socket.getBulb());
                }
                return 0xFFFFFF;
            }, ModBlocks.SOCKET.get());

            // Cable clamp of the stands (tint index 0): the colour of the carried cable.
            event.register((state, level, pos, tintIndex) ->
                            state.getBlock() instanceof net.fraeric.elnr.common.cable.StandBlock stand
                                    ? stand.getTintRgb() : 0xFFFFFF,
                    ModBlocks.STANDS.stream().map(stand -> stand.get())
                            .toArray(net.minecraft.world.level.block.Block[]::new));
        }

        @SubscribeEvent
        public static void registerItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event)
        {
            event.register((stack, tintIndex) ->
                            stack.getItem() instanceof net.minecraft.world.item.BlockItem item
                                    && item instanceof net.fraeric.elnr.common.machine.TubeLightItem tubeItem
                                    ? tubeItem.getRgb() : 0xFFFFFF,
                    net.fraeric.elnr.registry.ModMachines.TUBE_LIGHT_ITEMS.stream().map(i -> i.get())
                            .toArray(net.minecraft.world.level.ItemLike[]::new));
            event.register((stack, tintIndex) ->
                            stack.getItem() instanceof net.minecraft.world.item.BlockItem item
                                    && item.getBlock() instanceof net.fraeric.elnr.common.cable.StandBlock stand
                                    ? stand.getTintRgb() : 0xFFFFFF,
                    ModBlocks.STANDS.stream().map(stand -> stand.get().asItem())
                            .toArray(net.minecraft.world.level.ItemLike[]::new));
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerEntityRenderer(ModEntities.EXAMPLE_ENTITY.get(), ExampleEntityRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.CABLE.get(), CableBlockRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.STAND.get(), StandBlockRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.METER.get(), net.fraeric.elnr.client.MeterRenderer::new);
            event.registerBlockEntityRenderer(net.fraeric.elnr.registry.ModMachines.WIND_TURBINE_BE.get(),
                    net.fraeric.elnr.client.WindTurbineRenderer::new);
            event.registerBlockEntityRenderer(net.fraeric.elnr.registry.ModMachines.TUBE_LIGHT_BE.get(),
                    net.fraeric.elnr.client.TubeLightRenderer::new);
            event.registerBlockEntityRenderer(net.fraeric.elnr.registry.ModMachines.WIND_TURBINE_POLE_BE.get(),
                    net.fraeric.elnr.client.WindTurbinePoleRenderer::new);
        }
    }
}
