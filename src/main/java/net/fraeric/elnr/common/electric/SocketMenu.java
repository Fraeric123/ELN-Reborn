package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Menu of the bulb socket: a bulb slot, a cable slot and the player inventory (same layout as the street lamp). */
public class SocketMenu extends AbstractContainerMenu {

    public static final int BULB_X = 62;
    public static final int CABLE_X = 98;
    public static final int SLOT_Y = 34;

    private final BlockPos pos;
    private final Container container;
    @Nullable
    private final ContainerListener listener;

    /** Server side. */
    public SocketMenu(int id, Inventory inventory, SocketBlockEntity entity) {
        super(ModMenus.SOCKET.get(), id);
        this.pos = entity.getBlockPos();
        this.container = entity.getInventory();
        this.listener = c -> {
            if (entity.getLevel() != null && !entity.getLevel().isClientSide) {
                ElectricNetwork.updateAt(entity.getLevel(), pos);
            }
        };
        if (container instanceof SimpleContainer simple) {
            simple.addListener(listener);
        }
        init(inventory);
    }

    /** Client side. */
    public SocketMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.SOCKET.get(), id);
        this.pos = pos;
        this.container = new SimpleContainer(2);
        this.listener = null;
        init(inventory);
    }

    private void init(Inventory inventory) {
        addSlot(new Slot(container, SocketBlockEntity.BULB_SLOT, BULB_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BulbItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, SocketBlockEntity.CABLE_SLOT, CABLE_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CableItem.isPowerCable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof BulbItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (CableItem.isPowerCable(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (listener != null && container instanceof SimpleContainer simple) {
            simple.removeListener(listener);
        }
    }
}
