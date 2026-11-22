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
 * Logic of the timer block. Mode {@code DELAY}: once the input is powered the output
 * closes after {@code delay} ticks and opens again as soon as the power is gone.
 * Mode {@code CYCLE}: while the input is powered the output closes for {@code on}
 * ticks and opens for {@code off} ticks, over and over. A voltage on the disable
 * input freezes the output (and the countdown) in its current state.
 */
public class TimerBlockEntity extends BlockEntity {

    public static final int MODE_DELAY = 0;
    public static final int MODE_CYCLE = 1;
    public static final int MAX_TICKS = 72000;
    private static final int POLL_INTERVAL = 4;
    private static final double SIGNAL_VOLTAGE = 5.0D;

    private int mode = MODE_DELAY;
    private int delay = 40;
    private int onTicks = 20;
    private int offTicks = 20;

    private int counter = 0;
    private boolean powered = false;
    private boolean disabled = false;
    private int pollCooldown = 0;

    public TimerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIMER.get(), pos, state);
    }

    public int getMode() {
        return mode;
    }

    public int getDelay() {
        return delay;
    }

    public int getOnTicks() {
        return onTicks;
    }

    public int getOffTicks() {
        return offTicks;
    }

    public void toggleMode() {
        mode = mode == MODE_DELAY ? MODE_CYCLE : MODE_DELAY;
        counter = 0;
        setChanged();
    }

    public void setTicks(int field, int ticks) {
        int value = Math.max(1, Math.min(MAX_TICKS, ticks));
        switch (field) {
            case 0 -> delay = value;
            case 1 -> onTicks = value;
            default -> offTicks = value;
        }
        setChanged();
    }

    private boolean readTerminal(Direction side, Direction mountFace) {
        if (level == null) {
            return false;
        }
        BlockPos cablePos = worldPosition.relative(side);
        BlockState cableState = level.getBlockState(cablePos);
        if (!(cableState.getBlock() instanceof CableBlock cable) || !cable.hasFace(cableState, mountFace)) {
            return false;
        }
        ElectricNetwork.Analysis analysis = ElectricNetwork.analyze(level,
                new ElectricNetwork.Node(cablePos.immutable(), mountFace));
        return Math.abs(analysis.voltage()) >= SIGNAL_VOLTAGE;
    }

    private void setClosed(boolean closed) {
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof TimerBlock)
                || state.getValue(SwitchBlock.OPEN) == !closed) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, state.setValue(SwitchBlock.OPEN, !closed));
        ElectricNetwork.updateAt(level, worldPosition);
    }

    private boolean isClosed() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof TimerBlock && !state.getValue(SwitchBlock.OPEN);
    }

    public void serverTick() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof TimerBlock)) {
            return;
        }
        if (--pollCooldown <= 0) {
            pollCooldown = POLL_INTERVAL;
            BlockState state = getBlockState();
            Direction terminal = SwitchBlock.terminal(state);
            Direction mountFace = SwitchBlock.mountFace(state);
            boolean nowPowered = readTerminal(terminal.getOpposite(), mountFace);
            disabled = readTerminal(TimerBlock.disableSide(state), mountFace);
            if (nowPowered != powered) {
                powered = nowPowered;
                counter = 0;
                if (powered && mode == MODE_CYCLE && !disabled) {
                    setClosed(true);
                }
            }
        }
        if (!powered) {
            if (isClosed()) {
                setClosed(false);
            }
            counter = 0;
            return;
        }
        if (disabled) {
            return;
        }
        counter++;
        if (mode == MODE_DELAY) {
            if (!isClosed() && counter >= delay) {
                setClosed(true);
            }
        } else if (isClosed()) {
            if (counter >= onTicks) {
                counter = 0;
                setClosed(false);
            }
        } else if (counter >= offTicks) {
            counter = 0;
            setClosed(true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mode", mode);
        tag.putInt("Delay", delay);
        tag.putInt("On", onTicks);
        tag.putInt("Off", offTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = tag.getInt("Mode");
        delay = tag.contains("Delay") ? tag.getInt("Delay") : 40;
        onTicks = tag.contains("On") ? tag.getInt("On") : 20;
        offTicks = tag.contains("Off") ? tag.getInt("Off") : 20;
    }
}
