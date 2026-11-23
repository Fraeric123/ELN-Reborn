package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.network.NumericMenu;
import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Slotless settings menu of the gravity generator. Data: 0 min %, 1 max %, 2 distance, 3 mode, 4 strength %, 5 active, 6 watts, 7 group size, 8 facing. */
public class GravityMenu extends AbstractContainerMenu implements NumericMenu {

    private final BlockPos pos;
    @Nullable
    private final GravityBlockEntity be;
    private final ContainerData data;

    public GravityMenu(int id, Inventory inv, GravityBlockEntity be) {
        super(ModMachines.GRAVITY_MENU.get(), id);
        this.be = be;
        this.pos = be.getBlockPos();
        this.data = be.data;
        addDataSlots(data);
    }

    public GravityMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMachines.GRAVITY_MENU.get(), id);
        this.be = null;
        this.pos = pos;
        this.data = new SimpleContainerData(GravityBlockEntity.DATA_COUNT);
        addDataSlots(data);
    }

    public int get(int i) {
        return data.get(i);
    }

    @Override
    public void applyValues(Player player, int[] values) {
        if (be == null || player.level().isClientSide || values.length < 3) {
            return;
        }
        be.applyConfig(values[0], values[1], values[2]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null || player.level().isClientSide) {
            return false;
        }
        if (id == 0) {
            be.cycleMode();
            return true;
        }
        if (id == 1) {
            be.cycleFacing();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && player.level().getBlockEntity(pos) instanceof GravityBlockEntity;
    }
}
