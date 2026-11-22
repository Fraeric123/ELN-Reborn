package net.fraeric.elnr.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base of every new electric machine. The {@link PowerGrid} calls these hooks once per
 * grid step (every 2 ticks). Energy is in joules, power in watts.
 */
public abstract class ElectricMachineBlockEntity extends BlockEntity {

    protected double busVoltage;
    /** Positive = consumes / charges, negative = delivers. */
    protected double lastWatts;
    long lastSig = Long.MIN_VALUE;

    protected ElectricMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Voltage this machine forces on the network (generator, FE converter), 0 = none. */
    public double fixedSourceVoltage() { return 0.0D; }
    /** Maximum power the machine can deliver as a fixed source. */
    public double sourceMaxWatts() { return 0.0D; }
    /** Terminal voltage of an accumulator (capacitor / battery), 0 = empty. */
    public double storageVoltage() { return 0.0D; }
    public double dischargeMaxW(double dt) { return 0.0D; }
    public double chargeRequestW(double volts, double dt) { return 0.0D; }
    /** Rated voltage; above 1.3x of it the machine is destroyed. 0 = not limited. */
    public double ratedVoltage() { return 0.0D; }
    public double demandWatts(double volts) { return 0.0D; }
    public boolean isStorage() { return false; }

    /** Result of a grid step: {@code watts} > 0 consumed/charged, < 0 delivered. */
    public void applyPower(double volts, double watts, double dt) {
        this.busVoltage = volts;
        this.lastWatts = watts;
    }

    public double getBusVoltage() { return busVoltage; }
    public double getLastWatts() { return lastWatts; }

    /** Current this machine draws from the network (counts into cable overload). */
    public double loadCurrent() {
        return lastWatts > 0 && busVoltage > 0 ? lastWatts / busVoltage : 0.0D;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        PowerGrid.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        PowerGrid.remove(this);
    }
}
