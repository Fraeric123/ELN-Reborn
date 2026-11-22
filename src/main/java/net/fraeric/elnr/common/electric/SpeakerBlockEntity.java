package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.registry.ModBlockEntities;
import net.fraeric.elnr.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Configuration and playback logic of a sound block. A rising signal edge starts the
 * sound, a falling one may stop it. Everything runs on the server; clients only get
 * the sound (and the stop packet).
 */
public class SpeakerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

    private int soundIndex = 0;
    /** Repeat the sound for as long as the signal is present. */
    private boolean repeat = false;
    /** A new pulse interrupts the running sound (otherwise sounds stack). */
    private boolean interrupt = true;
    /** The sound is cut when the signal drops. */
    private boolean stopOnOff = false;

    private boolean powered = false;
    private long nextReplay = 0L;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    private SpeakerKind kind() {
        return getBlockState().getBlock() instanceof SpeakerBlock block ? block.getKind() : SpeakerKind.BELL;
    }

    private String soundName() {
        String[] sounds = kind().sounds();
        return sounds[Math.max(0, Math.min(soundIndex, sounds.length - 1))];
    }

    public int getSoundIndex() {
        return soundIndex;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public boolean isInterrupt() {
        return interrupt;
    }

    public boolean isStopOnOff() {
        return stopOnOff;
    }

    public void setSoundIndex(int index) {
        this.soundIndex = Math.max(0, Math.min(index, kind().sounds().length - 1));
        setChanged();
    }

    public void toggleRepeat() {
        repeat = !repeat;
        setChanged();
    }

    public void toggleInterrupt() {
        interrupt = !interrupt;
        setChanged();
    }

    public void toggleStopOnOff() {
        stopOnOff = !stopOnOff;
        setChanged();
    }

    /** Called by the network solver whenever the voltage at the block is known. */
    public void setSignal(Level level, boolean signal) {
        if (signal == powered) {
            return;
        }
        powered = signal;
        if (signal) {
            if (interrupt) {
                stopSound();
            }
            play();
        } else if (stopOnOff) {
            stopSound();
        }
        setChanged();
    }

    public void play() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SoundEvent event = ModSounds.get(soundName());
        if (event == null) {
            return;
        }
        serverLevel.playSound(null, worldPosition, event, SoundSource.BLOCKS, kind().volume(), 1.0F);
        double seconds = ModSounds.DURATIONS.getOrDefault(soundName(), 1.0D);
        nextReplay = serverLevel.getGameTime() + Math.max(1L, Math.round(seconds * 20.0D));
    }

    public void stopSound() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(
                ModSounds.id(soundName()), SoundSource.BLOCKS);
        double range = 16.0D * Math.max(1.0F, kind().volume()) + 8.0D;
        serverLevel.getServer().getPlayerList().broadcast(null,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D,
                range, serverLevel.dimension(), packet);
    }

    public void serverTick() {
        if (repeat && powered && level != null && level.getGameTime() >= nextReplay) {
            play();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Sound", soundIndex);
        tag.putBoolean("Repeat", repeat);
        tag.putBoolean("Interrupt", interrupt);
        tag.putBoolean("StopOnOff", stopOnOff);
        tag.putBoolean("Powered", powered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        soundIndex = tag.getInt("Sound");
        repeat = tag.getBoolean("Repeat");
        interrupt = !tag.contains("Interrupt") || tag.getBoolean("Interrupt");
        stopOnOff = tag.getBoolean("StopOnOff");
        powered = tag.getBoolean("Powered");
    }
}
