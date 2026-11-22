package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Capacitor or battery. The stored energy travels inside the dropped item. */
public class StorageBlock extends MachineBlock {

    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    private final boolean battery;
    private final double voltage;
    /** Farads for a capacitor, ampere-hours for a battery. */
    private final double capacity;

    public StorageBlock(Properties properties, boolean battery, double voltage, double capacity) {
        super(properties);
        this.battery = battery;
        this.voltage = voltage;
        this.capacity = capacity;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    public boolean isBattery() { return battery; }
    public double getVoltage() { return voltage; }
    public double getCapacity() { return capacity; }

    /** Maximum stored energy in joules. */
    public double maxEnergy() {
        return battery ? voltage * capacity * 3600.0D : 0.5D * capacity * voltage * voltage;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageBlockEntity(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack stack = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof StorageBlockEntity be
                && be.getEnergy() > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Energy", be.getEnergy());
            stack.addTagElement("BlockEntityTag", tag);
        }
        return List.of(stack);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof StorageBlockEntity be && be.getEnergy() > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Energy", be.getEnergy());
            stack.addTagElement("BlockEntityTag", tag);
        }
        return stack;
    }
}
