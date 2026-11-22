package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Slotless menu of the timer. Button ids: {@code 1} toggles the mode, an id of
 * {@code 1000 + field * 1000000 + ticks} sets field 0 (delay), 1 (on) or 2 (off).
 */
public class TimerMenu extends AbstractContainerMenu implements net.fraeric.elnr.network.NumericMenu {

    public static final int BTN_MODE = 1;
    public static final int FIELD_BASE = 1000;
    public static final int FIELD_STEP = 1_000_000;

    private final BlockPos pos;
    @Nullable
    private final TimerBlockEntity entity;
    private final ContainerData data;

    public TimerMenu(int id, Inventory inventory, TimerBlockEntity entity) {
        super(ModMenus.TIMER.get(), id);
        this.entity = entity;
        this.pos = entity.getBlockPos();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> entity.getMode();
                    case 1 -> entity.getDelay();
                    case 2 -> entity.getOnTicks();
                    default -> entity.getOffTicks();
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        addDataSlots(data);
    }

    public TimerMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.TIMER.get(), id);
        this.entity = null;
        this.pos = pos;
        this.data = new SimpleContainerData(4);
        addDataSlots(data);
    }

    public int getMode() {
        return data.get(0);
    }

    /** @return ticks of field 0 (delay), 1 (on time) or 2 (off time). */
    public int getTicks(int field) {
        return data.get(1 + field);
    }

    public static int fieldId(int field, int ticks) {
        return FIELD_BASE + field * FIELD_STEP + ticks;
    }

    @Override
    public void applyValues(Player player, int[] values) {
        if (entity == null || player.level().isClientSide) {
            return;
        }
        for (int i = 0; i < 3 && i < values.length; i++) {
            entity.setTicks(i, values[i]);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || player.level().isClientSide) {
            return false;
        }
        if (id == BTN_MODE) {
            entity.toggleMode();
            return true;
        }
        if (id >= FIELD_BASE) {
            int rest = id - FIELD_BASE;
            int field = rest / FIELD_STEP;
            if (field > 2) {
                return false;
            }
            entity.setTicks(field, rest % FIELD_STEP);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
