package net.fraeric.elnr.common.machine;

import net.minecraft.world.SimpleContainer;

/** Implemented by machine block entities with an item inventory. */
public interface MachineInventory {
    SimpleContainer getInventory();
}
