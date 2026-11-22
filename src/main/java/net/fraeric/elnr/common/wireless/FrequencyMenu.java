package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.common.machine.MachineMenu;
import net.fraeric.elnr.registry.ModWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Frequency screen of the wireless switch and the transmitter. */
public class FrequencyMenu extends MachineMenu implements net.fraeric.elnr.network.NumericMenu {

    private final BlockEntity be;

    /** Server. */
    public FrequencyMenu(int id, Inventory inv, BlockEntity be) {
        super(ModWireless.FREQUENCY_MENU.get(), id, be.getBlockPos(), new SimpleContainer(0), new SimpleContainerData(1), 0);
        this.be = be;
        if (be instanceof FrequencyHolder holder) {
            data.set(0, holder.getFrequency());
        }
        addPlayerSlots(inv, 84);
    }

    /** Client. */
    public FrequencyMenu(int id, Inventory inv, BlockPos pos) {
        super(ModWireless.FREQUENCY_MENU.get(), id, pos, new SimpleContainer(0), new SimpleContainerData(1), 0);
        this.be = null;
        addPlayerSlots(inv, 84);
    }

    @Override
    public void broadcastChanges() {
        if (be instanceof FrequencyHolder holder) {
            data.set(0, holder.getFrequency());
        }
        super.broadcastChanges();
    }

    @Override
    public void applyValues(Player player, int[] values) {
        if (player.level().isClientSide || values.length < 1 || !(be instanceof FrequencyHolder holder)) {
            return;
        }
        int next = Math.max(0, Math.min(FrequencyHolder.MAX_FREQUENCY, values[0]));
        holder.setFrequency(next);
        data.set(0, next);
    }

    @Override
    public List<Component> infoLines() {
        return List.of(Component.translatable("gui.elnr.wireless.frequency", get(0)),
                Component.translatable("gui.elnr.wireless.range", (int) WirelessManager.RANGE));
    }

    @Override
    public List<Component> buttonLabels() {
        return List.of();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide || !(be instanceof FrequencyHolder holder)) {
            return false;
        }
        int next = FrequencyButtons.apply(holder.getFrequency(), id);
        if (next < 0) {
            return false;
        }
        holder.setFrequency(next);
        data.set(0, next);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && player.level().getBlockEntity(pos) instanceof FrequencyHolder;
    }
}
