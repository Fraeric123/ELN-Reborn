package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class GeneratorMenu extends MachineMenu {

    private final GeneratorBlockEntity be;

    /** Server. */
    public GeneratorMenu(int id, Inventory inv, GeneratorBlockEntity be) {
        super(ModMachines.GENERATOR_MENU.get(), id, be.getBlockPos(), be.getInventory(), be.data, 1);
        this.be = be;
        setup(inv);
    }

    /** Client. */
    public GeneratorMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMachines.GENERATOR_MENU.get(), id, pos, new SimpleContainer(1), new SimpleContainerData(3), 1);
        this.be = null;
        setup(inv);
    }

    private void setup(Inventory inv) {
        addSlot(new Slot(container, 0, 80, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return GeneratorBlockEntity.isFuel(stack);
            }
        });
        addPlayerSlots(inv, 84);
    }

    @Override
    public List<Component> infoLines() {
        int idx = get(0);
        return List.of(
                Component.translatable("gui.elnr.generator.voltage", (int) GeneratorBlockEntity.VOLTAGES[idx],
                        (int) GeneratorBlockEntity.POWERS[idx]),
                Component.translatable("gui.elnr.generator.output", get(1)),
                Component.translatable("gui.elnr.generator.fuel", get(2) / 20));
    }

    @Override
    public List<Component> buttonLabels() {
        return List.of(Component.translatable("gui.elnr.generator.cycle"));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null && id == 0 && !player.level().isClientSide) {
            be.cycleVoltage();
            return true;
        }
        return false;
    }
}
