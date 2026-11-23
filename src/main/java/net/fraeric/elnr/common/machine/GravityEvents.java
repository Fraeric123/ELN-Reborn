package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.client.GravityClientHook;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Moves the player inside the gravity columns. Vanilla gravity is cancelled and replaced by the sum of
 * all fields the player stands in. The movement of the local player is decided by its client, so the
 * velocity is changed there; the server only keeps the player from being kicked / hurt for floating.
 */
@Mod.EventBusSubscriber(modid = ELNReborn.MODID)
public final class GravityEvents {

    /** What one tick of vanilla gravity (0.08, then 0.98 drag) took from the vertical velocity. */
    private static final double VANILLA_PULL = 0.08D * 0.98D;
    private static final double ACCEL = 0.08D;

    private GravityEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        Level level = player.level();
        boolean client = level.isClientSide;
        if ((client && !GravityClientHook.isLocal(player)) || player.isSpectator()) {
            return;
        }
        GravityBlockEntity.Sample sample = GravityBlockEntity.sample(level, player.getBoundingBox());
        if (!sample.any()) {
            return;
        }
        player.resetFallDistance();
        if (!client) {
            if (player instanceof ServerPlayer sp) {
                resetFlying(sp);
            }
            return;
        }
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || player.isInWater() || player.isInLava()) {
            return;
        }
        apply(player, sample.force());
    }

    private static java.lang.reflect.Field flyingField;
    private static boolean flyingLookedUp;

    /** Keeps the server from kicking a hovering player ("Flying is not enabled"); silently does nothing if the field is not found. */
    private static void resetFlying(ServerPlayer sp) {
        try {
            if (!flyingLookedUp) {
                flyingLookedUp = true;
                java.lang.reflect.Field f = sp.connection.getClass().getDeclaredField("aboveGroundTickCount");
                f.setAccessible(true);
                flyingField = f;
            }
            if (flyingField != null) {
                flyingField.setInt(sp.connection, 0);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            flyingField = null;
        }
    }

    private static void apply(Player p, Vec3 force) {
        Vec3 dm = p.getDeltaMovement();
        if (!p.isNoGravity()) {
            dm = dm.add(0.0D, VANILLA_PULL, 0.0D);
        }
        dm = dm.add(force.scale(ACCEL));
        double mag = force.length();
        boolean jumping = GravityClientHook.jumping();
        if (mag < 0.02D) {
            // weightless: space rises, sneak sinks
            if (jumping) {
                dm = dm.add(0.0D, 0.01D, 0.0D);
            }
            if (p.isShiftKeyDown()) {
                dm = dm.add(0.0D, -0.01D, 0.0D);
            }
        } else {
            Direction dir = Direction.getNearest(force.x, force.y, force.z);
            if (dir != Direction.DOWN) {
                // vanilla only knows the floor: emulate "standing" on the surface the field pulls us to
                Vec3 n = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                boolean contact = !p.level().noCollision(p, p.getBoundingBox().move(n.scale(0.05D)));
                if (contact) {
                    if (jumping) {
                        double v = Mth.clamp(0.42D * Math.sqrt(mag), 0.2D, 0.9D);
                        dm = dm.add(n.scale(-v));
                    } else {
                        p.setOnGround(true);
                        if (dir.getAxis().isHorizontal()) {
                            // walking on a wall: forward follows the look direction up / down the wall
                            double ly = Mth.clamp(p.getLookAngle().y * 1.5D, -1.0D, 1.0D);
                            dm = new Vec3(dm.x, dm.y * 0.6D + ly * GravityClientHook.forward() * 0.086D, dm.z);
                        }
                    }
                }
            }
        }
        p.setDeltaMovement(dm);
    }
}
