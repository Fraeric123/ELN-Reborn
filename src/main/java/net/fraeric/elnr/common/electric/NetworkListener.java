package net.fraeric.elnr.common.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A wall device that reads the voltage and current of the cable network it touches. */
public interface NetworkListener {

    /**
     * @param voltage network voltage in volts
     * @param current total network current in amperes
     */
    void onNetwork(Level level, BlockPos pos, double voltage, double current);
}
