package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * One generator. Power is P = U^2 / R (capped by what the settings need); the strength of the
 * gravity is the power that really arrives: g = P / WATTS_PER_G. Below the configured minimum the
 * generator is simply off, above the maximum the surplus is not used.
 */
public class GravityBlockEntity extends ElectricMachineBlockEntity {

    public static final double RESISTANCE = 100.0D;
    /** Power of one block for 1.0 g (normal gravity). */
    public static final double WATTS_PER_G = 200.0D;
    public static final double ZERO_G_WATTS = 150.0D;
    public static final int MODE_PULL = 0;
    public static final int MODE_PUSH = 1;
    public static final int MODE_ZERO = 2;
    public static final int MAX_PERCENT = 1000;
    public static final int MAX_DISTANCE = 64;
    public static final int DATA_COUNT = 9;

    public record Sample(boolean any, Vec3 force) {
    }

    private static final Sample NONE = new Sample(false, Vec3.ZERO);
    /** Loaded generators of both sides (the client needs them to move the player). */
    private static final Map<Level, Set<GravityBlockEntity>> FIELDS = new WeakHashMap<>();

    private int minPercent = 50;
    private int maxPercent = 100;
    private int distance = 16;
    private int mode = MODE_PULL;
    /** Synced to the client: is the field running and how strong (percent of 1 g, + = towards the block). */
    private boolean active;
    private int percent;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> minPercent;
                case 1 -> maxPercent;
                case 2 -> distance;
                case 3 -> mode;
                case 4 -> percent;
                case 5 -> active ? 1 : 0;
                case 6 -> (int) Math.min(100000, Math.round(Math.max(0.0D, lastWatts)));
                case 7 -> level == null ? 1 : GravityBlock.group(level, worldPosition).size();
                case 8 -> getBlockState().getValue(GravityBlock.FACING).get3DDataValue();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public GravityBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.GRAVITY_BE.get(), pos, state);
    }

    // ---- field lookup -------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            synchronized (FIELDS) {
                FIELDS.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            synchronized (FIELDS) {
                Set<GravityBlockEntity> set = FIELDS.get(level);
                if (set != null) {
                    set.remove(this);
                }
            }
        }
    }

    public Direction facing() {
        return getBlockState().getValue(GravityBlock.FACING);
    }

    /** Column of the field: the cross-section of the block, from its face up to {@code distance} blocks. */
    public AABB fieldBox() {
        Direction f = facing();
        return new AABB(worldPosition).expandTowards(f.getStepX() * (double) distance,
                f.getStepY() * (double) distance, f.getStepZ() * (double) distance);
    }

    /** Acceleration of this field in units of 1 g (a vector; it points to the block for + strength). */
    public Vec3 force() {
        Direction f = facing();
        double g = percent / 100.0D;
        return new Vec3(-f.getStepX() * g, -f.getStepY() * g, -f.getStepZ() * g);
    }

    /** Sum of all running fields whose column touches {@code box}; overlapping fields add up. */
    public static Sample sample(Level level, AABB box) {
        List<GravityBlockEntity> list;
        synchronized (FIELDS) {
            Set<GravityBlockEntity> set = FIELDS.get(level);
            if (set == null || set.isEmpty()) {
                return NONE;
            }
            list = new ArrayList<>(set);
        }
        boolean any = false;
        Vec3 sum = Vec3.ZERO;
        for (GravityBlockEntity be : list) {
            if (be.isRemoved() || !be.active || !be.fieldBox().intersects(box)) {
                continue;
            }
            any = true;
            sum = sum.add(be.force());
        }
        return any ? new Sample(true, sum) : NONE;
    }

    // ---- power ----------------------------------------------------------------------------

    @Override
    public double demandWatts(double volts) {
        if (volts <= 0) {
            return 0.0D;
        }
        double cap = mode == MODE_ZERO ? ZERO_G_WATTS : WATTS_PER_G * maxPercent / 100.0D;
        return Math.min(cap, volts * volts / RESISTANCE);
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        double w = Math.max(0.0D, watts);
        boolean on;
        int pct = 0;
        if (mode == MODE_ZERO) {
            on = w >= ZERO_G_WATTS * 0.95D;
        } else {
            double g = w / WATTS_PER_G;
            on = g + 1.0E-6D >= minPercent / 100.0D;
            g = Math.min(g, maxPercent / 100.0D);
            pct = (int) Math.round(g * 100.0D);
            if (pct <= 0) {
                on = false;
            }
            pct = on ? (mode == MODE_PUSH ? -pct : pct) : 0;
        }
        if (on != active || pct != percent) {
            active = on;
            percent = pct;
            setChanged();
            sync();
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, 3);
        }
    }

    // ---- settings (shared by the whole group of touching generators) ------------------------

    private List<GravityBlockEntity> groupEntities() {
        List<GravityBlockEntity> out = new ArrayList<>();
        if (level == null) {
            out.add(this);
            return out;
        }
        for (BlockPos p : GravityBlock.group(level, worldPosition)) {
            if (level.getBlockEntity(p) instanceof GravityBlockEntity be) {
                out.add(be);
            }
        }
        if (!out.contains(this)) {
            out.add(this);
        }
        return out;
    }

    public void applyConfig(int min, int max, int dist) {
        int mx = Mth.clamp(max, 1, MAX_PERCENT);
        int mn = Mth.clamp(min, 0, mx);
        int d = Mth.clamp(dist, 1, MAX_DISTANCE);
        for (GravityBlockEntity be : groupEntities()) {
            be.minPercent = mn;
            be.maxPercent = mx;
            be.distance = d;
            be.setChanged();
            be.sync();
        }
    }

    public void cycleMode() {
        int next = (mode + 1) % 3;
        for (GravityBlockEntity be : groupEntities()) {
            be.mode = next;
            be.setChanged();
            be.sync();
        }
    }

    public void cycleFacing() {
        if (level == null) {
            return;
        }
        BlockState s = getBlockState();
        Direction next = Direction.from3DDataValue((s.getValue(GravityBlock.FACING).get3DDataValue() + 1) % 6);
        level.setBlock(worldPosition, s.setValue(GravityBlock.FACING, next), 3);
    }

    public void copyConfig(GravityBlockEntity o) {
        minPercent = o.minPercent;
        maxPercent = o.maxPercent;
        distance = o.distance;
        mode = o.mode;
        setChanged();
    }

    // ---- save / sync -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Min", minPercent);
        tag.putInt("Max", maxPercent);
        tag.putInt("Dist", distance);
        tag.putInt("Mode", mode);
        tag.putBoolean("Active", active);
        tag.putInt("Percent", percent);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        maxPercent = Mth.clamp(tag.contains("Max") ? tag.getInt("Max") : 100, 1, MAX_PERCENT);
        minPercent = Mth.clamp(tag.contains("Min") ? tag.getInt("Min") : 50, 0, maxPercent);
        distance = Mth.clamp(tag.contains("Dist") ? tag.getInt("Dist") : 16, 1, MAX_DISTANCE);
        mode = Mth.clamp(tag.getInt("Mode"), 0, 2);
        active = tag.getBoolean("Active");
        percent = tag.getInt("Percent");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
