package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.common.machine.MachineMenu;
import net.fraeric.elnr.registry.ModWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Frequency screen of the remote control (opened with sneak + right click). */
public class RemoteMenu extends MachineMenu implements net.fraeric.elnr.network.NumericMenu {

    private final Player player;
    private final InteractionHand hand;

    public RemoteMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModWireless.REMOTE_MENU.get(), id, BlockPos.ZERO, new SimpleContainer(0), new SimpleContainerData(1), 0);
        this.player = inv.player;
        this.hand = hand;
        if (!player.level().isClientSide) {
            data.set(0, RemoteItem.getFrequency(player.getItemInHand(hand)));
        }
        addPlayerSlots(inv, 84);
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide) {
            data.set(0, RemoteItem.getFrequency(player.getItemInHand(hand)));
        }
        super.broadcastChanges();
    }

    @Override
    public void applyValues(Player p, int[] values) {
        if (p.level().isClientSide || values.length < 1) {
            return;
        }
        ItemStack stack = p.getItemInHand(hand);
        if (!(stack.getItem() instanceof RemoteItem)) {
            return;
        }
        int next = Math.max(0, Math.min(FrequencyHolder.MAX_FREQUENCY, values[0]));
        RemoteItem.setFrequency(stack, next);
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
    public boolean clickMenuButton(Player p, int id) {
        if (p.level().isClientSide) {
            return false;
        }
        ItemStack stack = p.getItemInHand(hand);
        if (!(stack.getItem() instanceof RemoteItem)) {
            return false;
        }
        int next = FrequencyButtons.apply(RemoteItem.getFrequency(stack), id);
        if (next < 0) {
            return false;
        }
        RemoteItem.setFrequency(stack, next);
        data.set(0, next);
        return true;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.getItemInHand(hand).getItem() instanceof RemoteItem;
    }
}
