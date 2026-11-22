package net.fraeric.elnr.common.wireless;

import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.electric.SwitchBlock;
import net.fraeric.elnr.registry.ModWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WirelessSwitchBlockEntity extends BlockEntity implements FrequencyHolder, WirelessReceiver {

    private int frequency = 1;
    private boolean toggled;
    private boolean txActive;

    public WirelessSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModWireless.WIRELESS_SWITCH_BE.get(), pos, state);
    }

    @Override public int getFrequency() { return frequency; }

    @Override
    public void setFrequency(int value) {
        frequency = Math.max(0, Math.min(MAX_FREQUENCY, value));
        txActive = false;
        setChanged();
        apply();
    }

    @Override
    public void setTxActive(boolean active) {
        txActive = active;
        apply();
    }

    @Override
    public void pulse() {
        toggled = !toggled;
        setChanged();
        apply();
    }

    private void apply() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean closed = toggled || txActive;
        BlockState state = getBlockState();
        if (state.hasProperty(SwitchBlock.OPEN) && state.getValue(SwitchBlock.OPEN) == closed) {
            level.setBlockAndUpdate(worldPosition, state.setValue(SwitchBlock.OPEN, !closed));
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, closed ? 0.6F : 0.5F);
            ElectricNetwork.updateAt(level, worldPosition);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        WirelessManager.add((WirelessReceiver) this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        WirelessManager.remove((WirelessReceiver) this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Frequency", frequency);
        tag.putBoolean("Toggled", toggled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        frequency = tag.contains("Frequency") ? tag.getInt("Frequency") : 1;
        toggled = tag.getBoolean("Toggled");
    }
}
