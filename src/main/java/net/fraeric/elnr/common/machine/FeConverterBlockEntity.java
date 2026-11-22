package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Joules (electric network) <-> Forge Energy converter. */
public class FeConverterBlockEntity extends ElectricMachineBlockEntity {

    public static final double JOULES_PER_FE = 2.0D;
    public static final double MAX_WATTS = 5000.0D;
    public static final int BUFFER = 20000;
    public static final double[] OUT_VOLTAGES = {50.0D, 200.0D, 800.0D, 3200.0D};
    /** Max FE moved per tick to/from neighbours (5 kW = 2500 FE/s). */
    private static final int FE_PER_TICK = (int) (MAX_WATTS / JOULES_PER_FE / 20.0D);

    private boolean fromFe;
    private int voltageIndex = 1;
    private double fe;

    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!fromFe) {
                return 0;
            }
            int accepted = (int) Math.min(Math.min(maxReceive, FE_PER_TICK), BUFFER - Math.floor(fe));
            if (accepted > 0 && !simulate) {
                fe += accepted;
                setChanged();
            }
            return Math.max(0, accepted);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (fromFe) {
                return 0;
            }
            int out = (int) Math.min(Math.min(maxExtract, FE_PER_TICK), Math.floor(fe));
            if (out > 0 && !simulate) {
                fe -= out;
                setChanged();
            }
            return Math.max(0, out);
        }

        @Override public int getEnergyStored() { return (int) fe; }
        @Override public int getMaxEnergyStored() { return BUFFER; }
        @Override public boolean canExtract() { return !fromFe; }
        @Override public boolean canReceive() { return fromFe; }
    };
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> storage);

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> fromFe ? 1 : 0;
                case 1 -> voltageIndex;
                case 2 -> (int) fe;
                case 3 -> (int) Math.min(5000, Math.round(Math.abs(lastWatts)));
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public FeConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.FE_BE.get(), pos, state);
    }

    public void toggleMode() {
        fromFe = !fromFe;
        refresh();
    }

    public void cycleVoltage() {
        voltageIndex = (voltageIndex + 1) % OUT_VOLTAGES.length;
        refresh();
    }

    private void refresh() {
        setChanged();
        if (level != null) {
            net.fraeric.elnr.common.cable.ElectricNetwork.updateAt(level, worldPosition);
        }
    }

    @Override
    public double fixedSourceVoltage() {
        return fromFe && fe >= 1 ? OUT_VOLTAGES[voltageIndex] : 0.0D;
    }

    @Override
    public double sourceMaxWatts() {
        return Math.min(MAX_WATTS, fe * JOULES_PER_FE / PowerGrid.DT);
    }

    @Override
    public double demandWatts(double volts) {
        if (fromFe || volts <= 0 || fe >= BUFFER) {
            return 0.0D;
        }
        return Math.min(MAX_WATTS, (BUFFER - fe) * JOULES_PER_FE / PowerGrid.DT);
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        if (watts != 0) {
            fe = Math.max(0.0D, Math.min(BUFFER, fe + watts * dt / JOULES_PER_FE));
            setChanged();
        }
    }

    public void serverTick() {
        if (level == null || fromFe) {
            if (fromFe) {
                pull();
            }
            return;
        }
        for (Direction dir : Direction.values()) {
            if (fe < 1) {
                break;
            }
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null || neighbour instanceof FeConverterBlockEntity) {
                continue;
            }
            neighbour.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(target -> {
                if (target.canReceive()) {
                    int sent = target.receiveEnergy((int) Math.min(FE_PER_TICK, fe), false);
                    fe -= sent;
                }
            });
        }
    }

    /** In FE -> J mode, neighbours that push do so on their own; this only pulls from extractors. */
    private void pull() {
        for (Direction dir : Direction.values()) {
            if (fe >= BUFFER) {
                return;
            }
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null || neighbour instanceof FeConverterBlockEntity) {
                continue;
            }
            neighbour.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(source -> {
                if (source.canExtract()) {
                    int got = source.extractEnergy((int) Math.min(FE_PER_TICK, BUFFER - fe), false);
                    fe += got;
                }
            });
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("FromFe", fromFe);
        tag.putInt("Voltage", voltageIndex);
        tag.putDouble("Fe", fe);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fromFe = tag.getBoolean("FromFe");
        voltageIndex = Math.max(0, Math.min(OUT_VOLTAGES.length - 1, tag.getInt("Voltage")));
        fe = tag.getDouble("Fe");
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }
}
