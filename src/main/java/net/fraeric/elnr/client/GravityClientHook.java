package net.fraeric.elnr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only reads of the local player's input (only called when the level is client side). */
@OnlyIn(Dist.CLIENT)
public final class GravityClientHook {

    private GravityClientHook() {
    }

    public static boolean isLocal(Player p) {
        return p == Minecraft.getInstance().player;
    }

    public static boolean jumping() {
        LocalPlayer pl = Minecraft.getInstance().player;
        return pl != null && pl.input.jumping;
    }

    public static double forward() {
        LocalPlayer pl = Minecraft.getInstance().player;
        return pl == null ? 0.0D : pl.input.forwardImpulse;
    }
}
