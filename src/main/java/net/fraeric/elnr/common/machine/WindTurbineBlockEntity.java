package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.fraeric.elnr.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Wind turbine. The taller the pole under it, the stronger the wind, the faster the rotor
 * spins and the more power it delivers. Inspired by the original ELN turbine (cut-in wind,
 * cubic wind power, rotor inertia) but simplified.
 */
public class WindTurbineBlockEntity extends ElectricMachineBlockEntity {

    public static final double VOLTAGE = 200.0D;
    /** Power of the original-size rotor (blade 1.9 blocks) at 12 m/s. */
    public static final double BASE_WATTS = 3000.0D;
    public static final double CUT_IN_WIND = 3.0D;
    public static final double RATED_WIND = 12.0D;
    /** Rotor speed (rad/s) per m/s of wind. */
    private static final double ROTOR_PER_WIND = 0.25D;
    private static final double MAX_RADS = 80.0D;
    /** From this speed on the rotor makes the fast sound (one turn takes about as long as the sound). */
    private static final double FAST_RADS = 3.3D;
    private static final int MIN_SOUND_GAP = 4;

    private double wind;
    private double rotorRads;
    private double availableWatts;
    private double syncedRads = -1.0D;
    private double revolution;
    private long lastSound;
    private int poleHeight;
    private int syncedPole = -1;

    // client-only animation state
    private float renderAngle;
    private float lastRenderTime = -1.0F;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.WIND_TURBINE_BE.get(), pos, state);
    }

    /** Blade length in blocks: the taller the tower, the bigger the rotor. */
    public static double bladeLength(int poleHeight) {
        return 1.5D + 0.1D * poleHeight;
    }

    /** Wind speed in m/s for a given pole height; grows without limit with the tower. */
    public static double windFor(Level level, BlockPos pos, int poleHeight) {
        double height = 0.8D + 0.85D * poleHeight;
        double t = level.getGameTime() / 20.0D;
        double gust = 0.72D + 0.14D * Math.sin(t / 37.0D) + 0.09D * Math.sin(t / 11.0D + 1.3D)
                + 0.05D * Math.sin(t / 3.7D + 4.0D);
        if (level.isThundering()) {
            gust += 0.25D;
        } else if (level.isRaining()) {
            gust += 0.1D;
        }
        double w = height * Math.max(0.0D, Math.min(1.25D, gust));
        if (!level.canSeeSky(pos.above())) {
            w *= 0.35D;
        }
        return w;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WindTurbineBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            be.poleHeight = WindTurbineTower.poleHeight(level, pos);
        }
        be.wind = windFor(level, pos, be.poleHeight);
        double target = be.wind >= CUT_IN_WIND ? Math.min(MAX_RADS, ROTOR_PER_WIND * be.wind) : 0.0D;
        be.rotorRads += (target - be.rotorRads) * (target > be.rotorRads ? 0.03D : 0.02D);
        if (be.rotorRads < 0.01D) {
            be.rotorRads = 0.0D;
        }
        if (be.wind >= CUT_IN_WIND && target > 0) {
            double w3 = be.wind * be.wind * be.wind - CUT_IN_WIND * CUT_IN_WIND * CUT_IN_WIND;
            double frac = w3 / (RATED_WIND * RATED_WIND * RATED_WIND - CUT_IN_WIND * CUT_IN_WIND * CUT_IN_WIND);
            double area = Math.pow(bladeLength(be.poleHeight) / 1.9D, 2.0D);
            double spool = Math.min(1.0D, be.rotorRads / target);
            be.availableWatts = BASE_WATTS * area * Math.max(0.0D, frac) * spool * spool;
        } else {
            be.availableWatts = 0.0D;
        }
        be.playRevolutionSound(level, pos, state);
        boolean rotorChanged = Math.abs(be.rotorRads - be.syncedRads) > 0.05D && level.getGameTime() % 10 == 0;
        if (rotorChanged || be.poleHeight != be.syncedPole) {
            be.syncedRads = be.rotorRads;
            be.syncedPole = be.poleHeight;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** One sound per rotor revolution (at most every few ticks), played from the rotor at the front of the block. */
    private void playRevolutionSound(Level level, BlockPos pos, BlockState state) {
        if (rotorRads < 0.1D) {
            revolution = 0.0D;
            return;
        }
        revolution += rotorRads * 0.05D;
        if (revolution < 2.0D * Math.PI) {
            return;
        }
        revolution %= 2.0D * Math.PI;
        if (level.getGameTime() - lastSound < MIN_SOUND_GAP) {
            return;
        }
        lastSound = level.getGameTime();
        Direction facing = state.getValue(WindTurbineBlock.FACING);
        level.playSound(null, pos.getX() + 0.5D + facing.getStepX() * 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D + facing.getStepZ() * 0.5D,
                ModSounds.get(rotorRads >= FAST_RADS ? "windturbine_big_ff" : "windturbine_big_sf"),
                SoundSource.BLOCKS, (float) Math.min(6.0D, 1.5D + poleHeight / 30.0D), 1.0F);
    }

    @Override
    public double fixedSourceVoltage() {
        return availableWatts > 1.0D ? VOLTAGE : 0.0D;
    }

    @Override
    public double sourceMaxWatts() {
        return availableWatts;
    }

    public double getWind() { return wind; }
    public double getRotorRads() { return rotorRads; }
    public double getAvailableWatts() { return availableWatts; }
    public int getPoleHeight() { return poleHeight; }
    public double getBladeLength() { return bladeLength(poleHeight); }

    /** Client: advances the rotor angle (radians) by the elapsed time and returns it. */
    public float advanceAngle(float timeTicks) {
        if (lastRenderTime >= 0.0F) {
            renderAngle += (float) rotorRads * (timeTicks - lastRenderTime) / 20.0F;
        }
        lastRenderTime = timeTicks;
        return renderAngle;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(getBladeLength() + 0.6D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Rotor", rotorRads);
        tag.putInt("Pole", poleHeight);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rotorRads = tag.getDouble("Rotor");
        poleHeight = tag.getInt("Pole");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putDouble("Rotor", rotorRads);
        tag.putInt("Pole", poleHeight);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
