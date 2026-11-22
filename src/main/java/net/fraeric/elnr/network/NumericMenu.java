package net.fraeric.elnr.network;

import net.minecraft.world.entity.player.Player;

/** A menu that receives whole numbers typed into its screen (container button ids are only one byte wide). */
public interface NumericMenu {

    /** Called on the server with the values sent by the screen. */
    void applyValues(Player player, int[] values);
}
