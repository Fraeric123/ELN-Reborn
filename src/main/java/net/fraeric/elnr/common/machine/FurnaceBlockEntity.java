package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Electric furnace: 200 V / 2 kW nominal (R = 20 ohm), uses the vanilla smelting recipes. */
public class FurnaceBlockEntity extends ElectricMachineBlockEntity implements MachineInventory {

    public static final double RESISTANCE = 20.0D;
    public static final double RATED_VOLTAGE = 200.0D;
    /** Joules per recipe cooking tick (200 ticks at 2 kW = 20 kJ). */
    public static final double JOULES_PER_COOK_TICK = 100.0D;

    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == 0;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            FurnaceBlockEntity.this.setChanged();
        }
    };
    private final LazyOptional<InvWrapper> handler = LazyOptional.of(() -> new InvWrapper(inventory));
    private double progress;
    private int cookTime = 200;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) Math.min(32767, Math.round(progress / JOULES_PER_COOK_TICK));
                case 1 -> cookTime;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.FURNACE_BE.get(), pos, state);
    }

    @Override
    public SimpleContainer getInventory() { return inventory; }

    @Override
    public double ratedVoltage() { return RATED_VOLTAGE; }

    private Optional<SmeltingRecipe> recipe() {
        if (level == null || inventory.getItem(0).isEmpty()) {
            return Optional.empty();
        }
        SimpleContainer in = new SimpleContainer(inventory.getItem(0).copy());
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, in, level);
    }

    private boolean canSmelt(SmeltingRecipe recipe) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        ItemStack out = inventory.getItem(1);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    @Override
    public double demandWatts(double volts) {
        if (volts <= 0) {
            return 0.0D;
        }
        return recipe().filter(this::canSmelt).isPresent() ? volts * volts / RESISTANCE : 0.0D;
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        if (level == null || level.isClientSide) {
            return;
        }
        Optional<SmeltingRecipe> found = recipe().filter(this::canSmelt);
        boolean lit = found.isPresent() && watts > 0;
        if (found.isEmpty()) {
            progress = 0;
        } else if (watts > 0) {
            SmeltingRecipe recipe = found.get();
            cookTime = recipe.getCookingTime();
            progress += watts * dt;
            if (progress >= cookTime * JOULES_PER_COOK_TICK) {
                progress = 0;
                ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                ItemStack out = inventory.getItem(1);
                if (out.isEmpty()) {
                    inventory.setItem(1, result);
                } else {
                    out.grow(result.getCount());
                }
                inventory.getItem(0).shrink(1);
                if (inventory.getItem(0).isEmpty()) {
                    inventory.setItem(0, ItemStack.EMPTY);
                }
            }
        }
        BlockState state = getBlockState();
        if (state.hasProperty(FurnaceBlock.LIT) && state.getValue(FurnaceBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(FurnaceBlock.LIT, lit), Block.UPDATE_ALL);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Input", inventory.getItem(0).save(new CompoundTag()));
        tag.put("Output", inventory.getItem(1).save(new CompoundTag()));
        tag.putDouble("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.setItem(0, ItemStack.of(tag.getCompound("Input")));
        inventory.setItem(1, ItemStack.of(tag.getCompound("Output")));
        progress = tag.getDouble("Progress");
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
