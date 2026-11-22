package net.fraeric.elnr.common.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A device that holds one bulb and one cable. The cable item is the device's "cord":
 * once plugged in, the device connects to every cable piece of the same tier that
 * touches it, so the cable network reaches straight into the device.
 *
 * <p>Implemented by the bulb socket ({@link SocketBlockEntity}) and by the street
 * lamp ({@link StreetLampBlockEntity}).</p>
 */
public interface BulbHolder {

    boolean hasBulb();

    ItemStack getBulb();

    void setBulb(ItemStack stack);

    /** Destroys the bulb with sound and particles (used on overvoltage). */
    void breakBulb(Level level, BlockPos pos);

    boolean hasCable();

    ItemStack getCable();

    void setCable(ItemStack stack);
}
