package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;

import java.util.List;

public class FeConverterMenu extends MachineMenu {

    private final FeConverterBlockEntity be;

    public FeConverterMenu(int id, Inventory inv, FeConverterBlockEntity be) {
        super(ModMachines.FE_MENU.get(), id, be.getBlockPos(), new SimpleContainer(0), be.data, 0);
        this.be = be;
        addPlayerSlots(inv, 84);
    }

    public FeConverterMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMachines.FE_MENU.get(), id, pos, new SimpleContainer(0), new SimpleContainerData(4), 0);
        this.be = null;
        addPlayerSlots(inv, 84);
    }

    @Override
    public List<Component> infoLines() {
        boolean fromFe = get(0) == 1;
        return List.of(
                Component.translatable(fromFe ? "gui.elnr.fe.mode_from" : "gui.elnr.fe.mode_to"),
                Component.translatable("gui.elnr.fe.voltage", (int) FeConverterBlockEntity.OUT_VOLTAGES[get(1)]),
                Component.translatable("gui.elnr.fe.buffer", get(2), FeConverterBlockEntity.BUFFER),
                Component.translatable("gui.elnr.fe.power", get(3)));
    }

    @Override
    public List<Component> buttonLabels() {
        return List.of(Component.translatable("gui.elnr.fe.toggle"), Component.translatable("gui.elnr.fe.cycle"));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null || player.level().isClientSide) {
            return false;
        }
        if (id == 0) {
            be.toggleMode();
            return true;
        }
        if (id == 1) {
            be.cycleVoltage();
            return true;
        }
        return false;
    }
}
