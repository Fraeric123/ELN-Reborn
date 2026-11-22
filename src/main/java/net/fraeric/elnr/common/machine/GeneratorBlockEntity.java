package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeneratorBlockEntity extends ElectricMachineBlockEntity implements MachineInventory {

    public static final TagKey<Item> FUEL_TAG = ItemTags.create(new ResourceLocation(ELNReborn.MODID, "generator_fuel"));
    public static final double[] VOLTAGES = {50.0D, 200.0D, 800.0D};
    public static final double[] POWERS = {1000.0D, 2000.0D, 5000.0D};
    public static final double JOULES_PER_BURN_TICK = 100.0D;

    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            GeneratorBlockEntity.this.setChanged();
        }
    };
    private final LazyOptional<InvWrapper> handler = LazyOptional.of(() -> new InvWrapper(inventory));
    private int voltageIndex = 1;
    private double fuelJoules;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> voltageIndex;
                case 1 -> (int) Math.min(5000, Math.round(Math.max(0.0D, -lastWatts)));
                case 2 -> (int) Math.min(32767, Math.round(fuelJoules / JOULES_PER_BURN_TICK));
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.GENERATOR_BE.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.is(FUEL_TAG) && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    @Override
    public SimpleContainer getInventory() { return inventory; }

    public void cycleVoltage() {
        voltageIndex = (voltageIndex + 1) % VOLTAGES.length;
        setChanged();
        if (level != null) {
            net.fraeric.elnr.common.cable.ElectricNetwork.updateAt(level, worldPosition);
        }
    }

    private boolean hasFuel() {
        return fuelJoules > 0 || isFuel(inventory.getItem(0));
    }

    @Override
    public double fixedSourceVoltage() {
        return hasFuel() ? VOLTAGES[voltageIndex] : 0.0D;
    }

    @Override
    public double sourceMaxWatts() {
        return POWERS[voltageIndex];
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        if (watts >= 0) {
            return;
        }
        fuelJoules -= -watts * dt;
        while (fuelJoules < 0 && isFuel(inventory.getItem(0))) {
            ItemStack stack = inventory.getItem(0);
            fuelJoules += ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) * JOULES_PER_BURN_TICK;
            ItemStack remainder = stack.getCraftingRemainingItem();
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.setItem(0, remainder);
            }
        }
        if (fuelJoules < 0) {
            fuelJoules = 0;
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Fuel", inventory.getItem(0).save(new CompoundTag()));
        tag.putDouble("FuelJoules", fuelJoules);
        tag.putInt("Voltage", voltageIndex);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.setItem(0, ItemStack.of(tag.getCompound("Fuel")));
        fuelJoules = tag.getDouble("FuelJoules");
        voltageIndex = Math.max(0, Math.min(VOLTAGES.length - 1, tag.getInt("Voltage")));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }
}
