package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Settings and logic of the voltage / current limiter. The switch is closed only while the
 * measured voltage and current lie inside the configured [min, max] windows.
 */
public class LimiterBlockEntity extends BlockEntity {

    public static final int MAX_VOLTAGE = 3000;
    public static final int MAX_CURRENT = 1000;
    private static final int POLL_INTERVAL = 4;

    /** Fields: 0 = min V, 1 = max V, 2 = min A, 3 = max A. */
    private final int[] limits = {0, 300, 0, 15};
    private double voltage = 0.0D;
    private double current = 0.0D;
    private int poll = 0;

    public LimiterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIMITER.get(), pos, state);
    }

    public int getLimit(int field) {
        return limits[field];
    }

    public void setLimit(int field, int value) {
        limits[field] = Math.max(0, Math.min(field < 2 ? MAX_VOLTAGE : MAX_CURRENT, value));
        poll = 0;
        setChanged();
    }

    public void adjust(int field, int delta) {
        int max = field < 2 ? MAX_VOLTAGE : MAX_CURRENT;
        limits[field] = Math.max(0, Math.min(max, limits[field] + delta));
        poll = 0;
        setChanged();
    }

    public double getVoltage() {
        return voltage;
    }

    public double getCurrent() {
        return current;
    }

    public boolean isClosed() {
        return !getBlockState().getValue(SwitchBlock.OPEN);
    }

    public void serverTick() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof LimiterBlock block)) {
            return;
        }
        if (--poll > 0) {
            return;
        }
        poll = POLL_INTERVAL;
        BlockState state = getBlockState();
        Direction mountFace = SwitchBlock.mountFace(state);
        double v = 0.0D;
        double a = 0.0D;
        for (Direction side : block.terminalSides(state)) {
            BlockPos cablePos = worldPosition.relative(side);
            BlockState cableState = level.getBlockState(cablePos);
            if (!(cableState.getBlock() instanceof CableBlock cable) || !cable.hasFace(cableState, mountFace)) {
                continue;
            }
            ElectricNetwork.Analysis analysis = ElectricNetwork.analyze(level,
                    new ElectricNetwork.Node(cablePos.immutable(), mountFace));
            v = Math.max(v, Math.abs(analysis.voltage()));
            a = Math.max(a, Math.abs(analysis.current()));
        }
        voltage = v;
        current = a;
        boolean closed = !state.getValue(SwitchBlock.OPEN);
        // The minimum current is only checked while closed: an open switch has no current to measure.
        boolean ok = v >= limits[0] && v <= limits[1] && a <= limits[3] && (!closed || a >= limits[2]);
        if (ok == closed) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, state.setValue(SwitchBlock.OPEN, !ok));
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, ok ? 0.6F : 0.5F);
        ElectricNetwork.updateAt(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Limits", limits);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] saved = tag.getIntArray("Limits");
        if (saved.length == 4) {
            System.arraycopy(saved, 0, limits, 0, 4);
        }
    }
}
