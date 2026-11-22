package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.common.machine.ElectricMachineBlockEntity;
import net.fraeric.elnr.registry.ModWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TransmitterBlockEntity extends ElectricMachineBlockEntity implements FrequencyHolder, WirelessTransmitter {

    public static final double RESISTANCE = 500.0D;
    public static final double MIN_VOLTAGE = 12.0D;

    private int frequency = 1;
    private boolean transmitting;

    public TransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModWireless.TRANSMITTER_BE.get(), pos, state);
    }

    @Override public int getFrequency() { return frequency; }
    @Override public boolean isTransmitting() { return transmitting; }

    @Override
    public void setFrequency(int value) {
        frequency = Math.max(0, Math.min(MAX_FREQUENCY, value));
        setChanged();
    }

    @Override public double ratedVoltage() { return 300.0D; }

    @Override
    public double demandWatts(double volts) {
        return volts > 0 ? volts * volts / RESISTANCE : 0.0D;
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        boolean on = watts > 0 && volts >= MIN_VOLTAGE;
        transmitting = on;
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide && state.hasProperty(TransmitterBlock.ACTIVE)
                && state.getValue(TransmitterBlock.ACTIVE) != on) {
            level.setBlock(worldPosition, state.setValue(TransmitterBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        WirelessManager.add((WirelessTransmitter) this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        WirelessManager.remove((WirelessTransmitter) this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Frequency", frequency);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        frequency = tag.contains("Frequency") ? tag.getInt("Frequency") : 1;
    }
}
