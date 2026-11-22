package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FurnaceMenu extends MachineMenu {

    public FurnaceMenu(int id, Inventory inv, FurnaceBlockEntity be) {
        super(ModMachines.FURNACE_MENU.get(), id, be.getBlockPos(), be.getInventory(), be.data, 2);
        setup(inv);
    }

    public FurnaceMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMachines.FURNACE_MENU.get(), id, pos, new SimpleContainer(2), new SimpleContainerData(2), 2);
        setup(inv);
    }

    private void setup(Inventory inv) {
        addSlot(new Slot(container, 0, 56, 35));
        addSlot(new Slot(container, 1, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerSlots(inv, 84);
    }

    /** Cooking progress 0..24 for the arrow. */
    public int arrow() {
        int total = Math.max(1, get(1));
        return get(0) * 24 / total;
    }
}
