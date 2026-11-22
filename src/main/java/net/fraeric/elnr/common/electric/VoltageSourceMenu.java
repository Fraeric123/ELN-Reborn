package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of the voltage source. It has no item slots of its own; the single data
 * slot synchronises the configured voltage to the client (in centivolts) and the
 * "button" channel is used to send a new voltage back to the server.
 */
public class VoltageSourceMenu extends AbstractContainerMenu implements net.fraeric.elnr.network.NumericMenu {

    /** 1/100 V, so that the GUI can show two decimals. */
    public static final int MILLIVOLT_SCALE = 100;
    public static final double MAX_VOLTAGE = 3200.0D;

    @Nullable
    private final VoltageSourceBlockEntity blockEntity;
    private final BlockPos pos;
    private final ContainerData data = new SimpleContainerData(1);

    /** Server side constructor. */
    public VoltageSourceMenu(int windowId, Inventory inventory, VoltageSourceBlockEntity blockEntity) {
        super(ModMenus.VOLTAGE_SOURCE.get(), windowId);
        this.blockEntity = blockEntity;
        this.pos = blockEntity.getBlockPos();
        this.data.set(0, toData(blockEntity.getVoltage()));
        this.addDataSlots(data);
    }

    /** Client side constructor (the block entity is only used to seed the widget). */
    public VoltageSourceMenu(int windowId, Inventory inventory, BlockPos pos) {
        super(ModMenus.VOLTAGE_SOURCE.get(), windowId);
        this.blockEntity = null;
        this.pos = pos;
        if (inventory.player.level().getBlockEntity(pos) instanceof VoltageSourceBlockEntity source) {
            this.data.set(0, toData(source.getVoltage()));
        }
        this.addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    /** Configured voltage as it is currently shown to the player. */
    public double getVoltage() {
        return data.get(0) / (double) MILLIVOLT_SCALE;
    }

    /** The new voltage in centivolts (container button ids are only a byte wide, so it comes by packet). */
    @Override
    public void applyValues(Player player, int[] values) {
        if (values.length > 0) {
            clickMenuButton(player, values[0]);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide) {
            return false;
        }
        VoltageSourceBlockEntity source = blockEntity;
        if (source == null && player.level().getBlockEntity(pos) instanceof VoltageSourceBlockEntity found) {
            source = found;
        }
        if (source == null) {
            return false;
        }
        double voltage = clamp(id / (double) MILLIVOLT_SCALE);
        source.setVoltage(voltage);
        data.set(0, toData(voltage));
        net.minecraft.world.level.Level world = player.level();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof VoltageSourceBlock
                && state.getValue(VoltageSourceBlock.ACTIVE) != VoltageSourceBlock.isActive(voltage)) {
            world.setBlockAndUpdate(pos, state.setValue(VoltageSourceBlock.ACTIVE, VoltageSourceBlock.isActive(voltage)));
        }
        net.fraeric.elnr.common.cable.ElectricNetwork.updateAt(world, pos);
        return true;
    }

    @Override
    public void broadcastChanges() {
        if (blockEntity != null && blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide) {
            data.set(0, toData(blockEntity.getVoltage()));
        }
        super.broadcastChanges();
    }

    public static double clamp(double voltage) {
        return Math.max(0.0D, Math.min(MAX_VOLTAGE, voltage));
    }

    public static int toData(double voltage) {
        return (int) Math.round(clamp(voltage) * MILLIVOLT_SCALE);
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity != null) {
            return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(
                    blockEntity.getLevel(), blockEntity.getBlockPos()), player, null)
                    || blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity;
        }
        return player.level().getBlockEntity(pos) instanceof VoltageSourceBlockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Purely informational machine; nothing can be moved in or out.
        return ItemStack.EMPTY;
    }
}
