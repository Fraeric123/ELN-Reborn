package net.fraeric.elnr.common.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class VoltageSourceBlockEntity extends BlockEntity {

    private double voltage = 0.0D;

    public VoltageSourceBlockEntity(BlockPos pos, BlockState state) {
        super(net.fraeric.elnr.registry.ModBlockEntities.VOLTAGE_SOURCE.get(), pos, state);
    }

    public double getVoltage() {
        return voltage;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Voltage", voltage);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        voltage = tag.getDouble("Voltage");
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.getBlock() instanceof VoltageSourceBlock
                    && state.getValue(VoltageSourceBlock.ACTIVE) != VoltageSourceBlock.isActive(voltage)) {
                level.setBlockAndUpdate(worldPosition,
                        state.setValue(VoltageSourceBlock.ACTIVE, VoltageSourceBlock.isActive(voltage)));
            }
        }
    }
}
