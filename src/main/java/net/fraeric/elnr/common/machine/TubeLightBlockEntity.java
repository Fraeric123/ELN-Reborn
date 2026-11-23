package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.fraeric.elnr.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.List;

/**
 * Fluorescent lamp / neon strip. Joined tubes are one device: the lamp with the lowest position
 * (the leader) drives the whole group. A fluorescent lamp flickers for a random time after power
 * arrives (neon_lamp_flicker on every flash, neon_lamp_start_noise after every flash) and then
 * stays lit; a neon strip simply glows.
 */
public class TubeLightBlockEntity extends ElectricMachineBlockEntity {

    /** The tube does not start below this share of its rated voltage. */
    private static final double MIN_SHARE = 0.8D;
    private static final int POWER_TIMEOUT = 6;

    private enum Phase { OFF, FLICKER, ON }

    private Phase phase = Phase.OFF;
    private long lastPower = Long.MIN_VALUE / 2;
    private long flickerEnd;
    private long nextToggle;
    private boolean flashOn;
    private List<BlockPos> group;
    private BlockPos leader;
    private long nextGroupCheck;
    private int[] arms;
    private boolean dirty = true;
    private List<TubeLightGeometry.Box> boxCache;
    private int boxKey;

    public TubeLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.TUBE_LIGHT_BE.get(), pos, state);
    }

    public int[] getArms() {
        return arms;
    }

    /** Server: stores the arm types of the shape and sends them to the clients. */
    public void setArms(int[] value) {
        if (Arrays.equals(arms, value)) {
            return;
        }
        arms = value.clone();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Boxes of the shape (client rendering). */
    public List<TubeLightGeometry.Box> boxes() {
        int[] a = arms != null ? arms : TubeLightBlock.defaultArms(getBlockState());
        int key = Arrays.hashCode(a) * 31 + getBlockState().getValue(TubeLightBlock.FACING).ordinal();
        if (boxCache == null || key != boxKey) {
            boxKey = key;
            boxCache = TubeLightGeometry.boxes(getBlockState().getValue(TubeLightBlock.FACING), a,
                    block().getKind(), block().getThickness());
        }
        return boxCache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (arms != null) {
            tag.putIntArray("Arms", arms);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] a = tag.getIntArray("Arms");
        arms = a.length == 4 ? a : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (arms != null) {
            tag.putIntArray("Arms", arms);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private TubeLightBlock block() {
        return (TubeLightBlock) getBlockState().getBlock();
    }

    @Override
    public double ratedVoltage() {
        return block().getVoltage();
    }

    @Override
    public double demandWatts(double volts) {
        TubeLightBlock b = block();
        double rated = b.getVoltage();
        return volts >= MIN_SHARE * rated ? volts * volts / (rated * rated / b.getWatts()) : 0.0D;
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        if (level != null && watts > 0.0D && volts >= MIN_SHARE * block().getVoltage()) {
            lastPower = level.getGameTime();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TubeLightBlockEntity be) {
        long now = level.getGameTime();
        if (be.dirty) {
            be.dirty = false;
            TubeLightBlock.refresh(level, pos);
        }
        boolean powered = now - be.lastPower <= POWER_TIMEOUT;
        TubeLightBlock b = be.block();
        if (b.getKind() == TubeLightBlock.Kind.NEON) {
            setLit(level, pos, powered);
            return;
        }
        if (be.group == null || now >= be.nextGroupCheck) {
            be.group = TubeLightBlock.group(level, pos);
            be.nextGroupCheck = now + 10 + (pos.asLong() & 3);
            BlockPos lead = pos;
            for (BlockPos p : be.group) {
                if (p.asLong() < lead.asLong()) {
                    lead = p;
                }
            }
            be.leader = lead;
        }
        if (!pos.equals(be.leader)) {
            return;
        }
        if (!powered) {
            be.phase = Phase.OFF;
            be.setAll(level, false);
            return;
        }
        switch (be.phase) {
            case OFF -> {
                be.phase = Phase.FLICKER;
                // the whole lamp flickers for 1.5 - 6 seconds
                be.flickerEnd = now + 30 + level.random.nextInt(91);
                be.flashOn = false;
                be.nextToggle = now + 2 + level.random.nextInt(8);
            }
            case FLICKER -> {
                if (now >= be.flickerEnd) {
                    be.phase = Phase.ON;
                    be.setAll(level, true);
                } else if (now >= be.nextToggle) {
                    be.flashOn = !be.flashOn;
                    if (be.flashOn) {
                        be.nextToggle = now + 1 + level.random.nextInt(4);
                        be.sound(level, pos, "neon_lamp_flicker", 0.7F);
                    } else {
                        be.nextToggle = now + 3 + level.random.nextInt(10);
                        be.sound(level, pos, "neon_lamp_start_noise", 0.6F);
                    }
                    be.setAll(level, be.flashOn);
                }
            }
            case ON -> be.setAll(level, true);
        }
    }

    private void setAll(Level level, boolean lit) {
        for (BlockPos p : group) {
            setLit(level, p, lit);
        }
    }

    private void sound(Level level, BlockPos pos, String name, float volume) {
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                ModSounds.get(name), SoundSource.BLOCKS, volume, 1.0F);
    }

    private static void setLit(Level level, BlockPos pos, boolean lit) {
        BlockState cur = level.getBlockState(pos);
        if (cur.getBlock() instanceof TubeLightBlock && cur.getValue(TubeLightBlock.LIT) != lit) {
            level.setBlock(pos, cur.setValue(TubeLightBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }
}
