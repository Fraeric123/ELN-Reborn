package net.fraeric.elnr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: numbers typed into the open menu. */
public record MenuValuesPacket(int[] values) {

    private static final int MAX_VALUES = 8;

    public static void encode(MenuValuesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarIntArray(packet.values);
    }

    public static MenuValuesPacket decode(FriendlyByteBuf buf) {
        return new MenuValuesPacket(buf.readVarIntArray(MAX_VALUES));
    }

    public static void handle(MenuValuesPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.containerMenu instanceof NumericMenu menu) {
                menu.applyValues(player, packet.values);
            }
        });
        ctx.setPacketHandled(true);
    }
}
