package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu of a relay: one coil slot plus the player inventory. */
public class RelayMenu extends AbstractContainerMenu {

    public static final int COIL_X = 80;
    public static final int COIL_Y = 30;

    private final BlockPos pos;
    private final Container container;
    private final ContainerData data;

    /** Server side. */
    public RelayMenu(int id, Inventory inventory, RelayBlockEntity entity) {
        super(ModMenus.RELAY.get(), id);
        this.pos = entity.getBlockPos();
        this.container = entity.getContainer();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> entity.isEnergised() ? 1 : 0;
                    case 1 -> entity.coilState();
                    default -> (int) Math.round(entity.getMeasured() * 10.0D);
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        init(inventory);
    }

    /** Client side. */
    public RelayMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.RELAY.get(), id);
        this.pos = pos;
        this.container = new SimpleContainer(1);
        this.data = new SimpleContainerData(3);
        init(inventory);
    }

    private void init(Inventory inventory) {
        container.startOpen(inventory.player);
        addSlot(new Slot(container, 0, COIL_X, COIL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof RelayCoilItem;
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
        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isEnergised() {
        return data.get(0) != 0;
    }

    /** @return 0 = no coil, 1..4 = working coil, 5 = burnt coil. */
    public int getCoilState() {
        return data.get(1);
    }

    public double getMeasured() {
        return data.get(2) / 10.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof RelayCoilItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
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
        container.stopOpen(player);
    }
}
