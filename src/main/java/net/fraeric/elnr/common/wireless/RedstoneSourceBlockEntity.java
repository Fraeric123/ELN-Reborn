package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.common.machine.ElectricMachineBlockEntity;
import net.fraeric.elnr.registry.ModWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RedstoneSourceBlockEntity extends ElectricMachineBlockEntity {

    public static final double RESISTANCE = 1000.0D;
    public static final double MIN_VOLTAGE = 10.0D;

    public RedstoneSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModWireless.REDSTONE_BE.get(), pos, state);
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
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide && state.hasProperty(RedstoneSourceBlock.POWERED)
                && state.getValue(RedstoneSourceBlock.POWERED) != on) {
            level.setBlock(worldPosition, state.setValue(RedstoneSourceBlock.POWERED, on), Block.UPDATE_ALL);
        }
    }
}
