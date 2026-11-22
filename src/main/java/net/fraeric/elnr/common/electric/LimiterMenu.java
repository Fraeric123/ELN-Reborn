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
 * Slotless menu of the limiter. Button id = {@code field * 10 + step}, fields 0..3 are
 * min V, max V, min A, max A and the step indexes {@link #STEPS}.
 */
public class LimiterMenu extends AbstractContainerMenu implements net.fraeric.elnr.network.NumericMenu {

    public static final int[] STEPS = {-100, -10, -1, 1, 10, 100};

    private final BlockPos pos;
    @Nullable
    private final LimiterBlockEntity entity;
    private final ContainerData data;

    public LimiterMenu(int id, Inventory inventory, LimiterBlockEntity entity) {
        super(ModMenus.LIMITER.get(), id);
        this.entity = entity;
        this.pos = entity.getBlockPos();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0, 1, 2, 3 -> entity.getLimit(index);
                    case 4 -> entity.isClosed() ? 1 : 0;
                    case 5 -> (int) Math.round(entity.getVoltage() * 10.0D);
                    default -> (int) Math.round(entity.getCurrent() * 100.0D);
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 7;
            }
        };
        addDataSlots(data);
    }

    public LimiterMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.LIMITER.get(), id);
        this.entity = null;
        this.pos = pos;
        this.data = new SimpleContainerData(7);
        addDataSlots(data);
    }

    public int getLimit(int field) {
        return data.get(field);
    }

    public boolean isClosed() {
        return data.get(4) != 0;
    }

    public double getVoltage() {
        return data.get(5) / 10.0D;
    }

    public double getCurrent() {
        return data.get(6) / 100.0D;
    }

    @Override
    public void applyValues(Player player, int[] values) {
        if (entity == null || player.level().isClientSide || values.length < 4) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            entity.setLimit(i, values[i]);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
