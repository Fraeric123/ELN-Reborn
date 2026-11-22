package net.fraeric.elnr.registry;

import net.fraeric.elnr.common.item.TooltipBlockItem;
import net.fraeric.elnr.common.wireless.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.registries.RegistryObject;

/** C4, wireless switch, remote control, transmitter and the electric redstone source. */
public final class ModWireless {

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL);
    }

    public static final RegistryObject<Block> C4 = ModBlocks.BLOCKS.register("c4",
            () -> new C4Block(BlockBehaviour.Properties.of().strength(0.8F, 1.0F).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final RegistryObject<Item> C4_ITEM = ModItems.ITEMS.register("c4",
            () -> new TooltipBlockItem(C4.get(), new Item.Properties(), "tooltip.elnr.c4.1", "tooltip.elnr.c4.2"));

    public static final RegistryObject<Block> WIRELESS_SWITCH = ModBlocks.BLOCKS.register("wireless_switch",
            () -> new WirelessSwitchBlock(metal().noOcclusion()));
    public static final RegistryObject<Item> WIRELESS_SWITCH_ITEM = ModItems.ITEMS.register("wireless_switch",
            () -> new TooltipBlockItem(WIRELESS_SWITCH.get(), new Item.Properties(),
                    "tooltip.elnr.wireless_switch.1", "tooltip.elnr.wireless_switch.2"));

    public static final RegistryObject<Block> TRANSMITTER = ModBlocks.BLOCKS.register("wireless_transmitter",
            () -> new TransmitterBlock(metal()));
    public static final RegistryObject<Item> TRANSMITTER_ITEM = ModItems.ITEMS.register("wireless_transmitter",
            () -> new TooltipBlockItem(TRANSMITTER.get(), new Item.Properties(),
                    "tooltip.elnr.transmitter.1", "tooltip.elnr.transmitter.2"));

    public static final RegistryObject<Block> REDSTONE_SOURCE = ModBlocks.BLOCKS.register("electric_redstone_source",
            () -> new RedstoneSourceBlock(metal()));
    public static final RegistryObject<Item> REDSTONE_SOURCE_ITEM = ModItems.ITEMS.register("electric_redstone_source",
            () -> new TooltipBlockItem(REDSTONE_SOURCE.get(), new Item.Properties(),
                    "tooltip.elnr.redstone_source.1", "tooltip.elnr.redstone_source.2"));

    public static final RegistryObject<Item> REMOTE = ModItems.ITEMS.register("wireless_remote",
            () -> new RemoteItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<WirelessSwitchBlockEntity>> WIRELESS_SWITCH_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("wireless_switch",
                    () -> BlockEntityType.Builder.of(WirelessSwitchBlockEntity::new, WIRELESS_SWITCH.get()).build(null));
    public static final RegistryObject<BlockEntityType<TransmitterBlockEntity>> TRANSMITTER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("wireless_transmitter",
                    () -> BlockEntityType.Builder.of(TransmitterBlockEntity::new, TRANSMITTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<RedstoneSourceBlockEntity>> REDSTONE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("electric_redstone_source",
                    () -> BlockEntityType.Builder.of(RedstoneSourceBlockEntity::new, REDSTONE_SOURCE.get()).build(null));

    public static final RegistryObject<MenuType<FrequencyMenu>> FREQUENCY_MENU = ModMenus.MENUS.register("frequency",
            () -> IForgeMenuType.create((id, inv, buf) -> new FrequencyMenu(id, inv, buf.readBlockPos())));
    public static final RegistryObject<MenuType<RemoteMenu>> REMOTE_MENU = ModMenus.MENUS.register("wireless_remote",
            () -> IForgeMenuType.create((id, inv, buf) -> new RemoteMenu(id, inv, buf.readEnum(InteractionHand.class))));

    private ModWireless() {
    }

    public static void init() {
    }
}
