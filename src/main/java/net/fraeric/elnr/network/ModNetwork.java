package net.fraeric.elnr.network;

import net.fraeric.elnr.ELNReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;

public final class ModNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Objects.requireNonNull(ResourceLocation.tryBuild(ELNReborn.MODID, "main")),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean registered = false;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, MenuValuesPacket.class, MenuValuesPacket::encode,
                MenuValuesPacket::decode, MenuValuesPacket::handle);
    }

    /** Client side helper: sends numbers to the open menu. */
    public static void sendValues(int... values) {
        CHANNEL.sendToServer(new MenuValuesPacket(values));
    }
}
