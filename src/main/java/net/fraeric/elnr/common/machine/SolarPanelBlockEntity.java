package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Power of a solar panel follows the height of the sun, rain and a free view of the sky. */
public class SolarPanelBlockEntity extends ElectricMachineBlockEntity {

    private double sunFactor;
    private double availableWatts;
    /** Number of blocks of the whole connected panel (decides the voltage). */
    private int tiles = 1;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.SOLAR_PANEL_BE.get(), pos, state);
    }

    /** 0..1: how strongly the sun shines on a panel at {@code pos}. */
    public static double sunFactor(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight() || !level.canSeeSky(pos.above())) {
            return 0.0D;
        }
        double sun = Math.max(0.0D, Math.cos(level.getSunAngle(1.0F)));
        // a bit of twilight light, so the panel does not cut off sharply
        sun = Math.min(1.0D, Math.pow(sun, 0.6D));
        if (level.isThundering()) {
            sun *= 0.2D;
        } else if (level.isRaining()) {
            sun *= 0.45D;
        }
        return sun;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity be) {
        if (level.getGameTime() % 10 != 0 || !(state.getBlock() instanceof SolarPanelBlock)) {
            return;
        }
        be.sunFactor = sunFactor(level, pos);
        be.availableWatts = SolarPanelBlock.TILE_WATTS * be.sunFactor;
        be.tiles = SolarPanelBlock.connected(level, pos).size();
    }

    public double getSunFactor() { return sunFactor; }
    public double getAvailableWatts() { return availableWatts; }

    @Override
    public double fixedSourceVoltage() {
        return availableWatts > 0.5D ? SolarPanelBlock.voltageFor(tiles) : 0.0D;
    }

    @Override
    public double sourceMaxWatts() {
        return availableWatts;
    }
}
