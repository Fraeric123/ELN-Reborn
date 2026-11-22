package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StorageBlockEntity extends ElectricMachineBlockEntity {

    private double energy;

    public StorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.STORAGE_BE.get(), pos, state);
    }

    private StorageBlock block() {
        return (StorageBlock) getBlockState().getBlock();
    }

    public double getEnergy() { return energy; }

    public void setEnergy(double value) {
        energy = Math.max(0.0D, Math.min(block().maxEnergy(), value));
        setChanged();
    }

    @Override public boolean isStorage() { return true; }
    @Override public double ratedVoltage() { return block().getVoltage(); }

    @Override
    public double storageVoltage() {
        if (energy <= 0) {
            return 0.0D;
        }
        StorageBlock b = block();
        return b.isBattery() ? b.getVoltage() : b.getVoltage() * Math.sqrt(Math.min(1.0D, energy / b.maxEnergy()));
    }

    @Override
    public double dischargeMaxW(double dt) {
        StorageBlock b = block();
        double max = b.maxEnergy() / (b.isBattery() ? 150.0D : 3.0D);
        return Math.min(max, energy / dt);
    }

    @Override
    public double chargeRequestW(double volts, double dt) {
        StorageBlock b = block();
        double max = b.maxEnergy() / (b.isBattery() ? 300.0D : 3.0D);
        if (b.isBattery()) {
            if (volts < 0.8D * b.getVoltage() || energy >= b.maxEnergy()) {
                return 0.0D;
            }
            return Math.min(max, (b.maxEnergy() - energy) / dt);
        }
        double target = Math.min(b.maxEnergy(), 0.5D * b.getCapacity() * volts * volts);
        double need = target - energy;
        return need <= 0 ? 0.0D : Math.min(max, need / dt);
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        if (watts != 0.0D) {
            setEnergy(energy + watts * dt);
        }
        if (level != null && !level.isClientSide) {
            double max = block().maxEnergy();
            int charge = energy <= 0 ? 0 : 1 + (int) Math.min(3.0D, energy / max * 3.999D);
            BlockState state = getBlockState();
            if (state.getValue(StorageBlock.CHARGE) != charge) {
                level.setBlock(worldPosition, state.setValue(StorageBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Energy", energy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = tag.getDouble("Energy");
    }
}
