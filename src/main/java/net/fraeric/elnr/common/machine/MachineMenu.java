package net.fraeric.elnr.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Base menu of the machines with a GUI (generator, furnace, FE converter). */
public abstract class MachineMenu extends AbstractContainerMenu {

    protected final BlockPos pos;
    protected final Container container;
    protected final ContainerData data;
    protected final int machineSlots;

    protected MachineMenu(MenuType<?> type, int id, BlockPos pos, Container container, ContainerData data, int machineSlots) {
        super(type, id);
        this.pos = pos;
        this.container = container;
        this.data = data;
        this.machineSlots = machineSlots;
        addDataSlots(data);
    }

    protected void addPlayerSlots(Inventory inv, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, y + 58));
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public int get(int i) {
        return data.get(i);
    }

    /** Text lines shown by the generic panel screen. */
    public List<Component> infoLines() {
        return List.of();
    }

    /** Labels of the buttons of the generic panel screen; button i sends id i. */
    public List<Component> buttonLabels() {
        return List.of();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && player.level().getBlockEntity(pos) instanceof ElectricMachineBlockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
