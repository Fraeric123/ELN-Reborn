package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.common.cable.StandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * A wind turbine and its pole count as one electrical device. A pole layer is a square of
 * blocks around a centre block; the layers taper from the bottom to the top of the pole.
 */
public final class WindTurbineTower {

    /** Number of layers a pole can have. */
    public static final int MAX_POLE = 384;
    /** Widest layer in pixels (80 = 5 x 5 blocks). */
    public static final double MAX_WIDTH = 80.0D;
    /** Pixels of base width per block of pole height. */
    public static final double TAPER_SLOPE = 2.0D;
    /** The pole narrows to its top thickness at this fraction of its height (4/9); above it the thickness stays the same. */
    public static final double TAPER_FRACTION = 4.0D / 9.0D;
    /** Width in pixels of the straight top section. */
    public static final double TOP_WIDTH = 6.0D;
    private static final int PLACE_FLAGS = 2 | 16;

    private static boolean busy;
    private static int version;
    private static final Map<BlockGetter, Cache> CACHES = new WeakHashMap<>();

    private static final class Cache {
        int version = -1;
        final Map<Long, List<BlockPos>> map = new HashMap<>();
    }

    private WindTurbineTower() {
    }

    /** True while the tower changes its own blocks (the blocks then do not react to it). */
    public static boolean isBusy() {
        return busy;
    }

    private static void touch() {
        synchronized (CACHES) {
            version++;
        }
    }

    private static boolean isPole(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof WindTurbinePoleBlock;
    }

    private static boolean isCenter(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return st.getBlock() instanceof WindTurbinePoleBlock && WindTurbinePoleBlock.isCenter(st);
    }

    private static boolean isTurbine(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof WindTurbineBlock;
    }

    /** Centre block of the layer {@code pos} belongs to, or null. */
    public static BlockPos centerOf(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof WindTurbinePoleBlock)) {
            return null;
        }
        BlockPos c = pos.offset(2 - st.getValue(WindTurbinePoleBlock.OX), 0, 2 - st.getValue(WindTurbinePoleBlock.OZ));
        return isCenter(level, c) ? c : null;
    }

    /** The wind turbine sitting on top of the tower that {@code pos} belongs to, or null. */
    public static BlockPos turbineOf(BlockGetter level, BlockPos pos) {
        for (BlockPos m : members(level, pos)) {
            if (isTurbine(level, m)) {
                return m;
            }
        }
        return null;
    }

    /** Number of pole layers standing directly under {@code turbinePos}. */
    public static int poleHeight(BlockGetter level, BlockPos turbinePos) {
        int n = 0;
        BlockPos p = turbinePos.below();
        while (n < MAX_POLE && isCenter(level, p)) {
            n++;
            p = p.below();
        }
        return n;
    }

    private static void addLayer(BlockGetter level, BlockPos center, List<BlockPos> out) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                BlockState st = level.getBlockState(p);
                if (st.getBlock() instanceof WindTurbinePoleBlock
                        && st.getValue(WindTurbinePoleBlock.OX) == dx + 2 && st.getValue(WindTurbinePoleBlock.OZ) == dz + 2) {
                    out.add(p);
                }
            }
        }
    }

    /** Every block that belongs to the same tower as {@code pos} (just {@code pos} for other blocks). */
    public static List<BlockPos> members(BlockGetter level, BlockPos pos) {
        Block b = level.getBlockState(pos).getBlock();
        if (b instanceof TubeLightBlock) {
            return TubeLightBlock.group(level, pos);
        }
        if (!(b instanceof WindTurbinePoleBlock) && !(b instanceof WindTurbineBlock)) {
            return List.of(pos);
        }
        synchronized (CACHES) {
            Cache cache = CACHES.computeIfAbsent(level, k -> new Cache());
            if (cache.version != version) {
                cache.map.clear();
                cache.version = version;
            }
            List<BlockPos> hit = cache.map.get(pos.asLong());
            if (hit != null) {
                return hit;
            }
            List<BlockPos> list = compute(level, pos, b);
            cache.map.put(pos.asLong(), list);
            return list;
        }
    }

    private static List<BlockPos> compute(BlockGetter level, BlockPos pos, Block b) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos top;
        if (b instanceof WindTurbineBlock) {
            out.add(pos);
            top = pos.below();
            if (!isCenter(level, top)) {
                return Collections.unmodifiableList(out);
            }
        } else {
            BlockPos center = centerOf(level, pos);
            if (center == null) {
                return List.of(pos);
            }
            top = center;
            int guard = 0;
            while (guard++ < MAX_POLE && isCenter(level, top.above())) {
                top = top.above();
            }
            if (isTurbine(level, top.above())) {
                out.add(top.above());
            }
        }
        int n = 0;
        for (BlockPos c = top; n < MAX_POLE && isCenter(level, c); c = c.below(), n++) {
            addLayer(level, c, out);
        }
        return Collections.unmodifiableList(out);
    }

    /** Removes every block of the layer except {@code removed}; the centre block drops the item. */
    static void breakLayer(Level level, BlockPos center, BlockPos removed) {
        boolean was = busy;
        busy = true;
        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = center.offset(dx, 0, dz);
                    if (p.equals(removed)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof WindTurbinePoleBlock
                            && st.getValue(WindTurbinePoleBlock.OX) == dx + 2 && st.getValue(WindTurbinePoleBlock.OZ) == dz + 2) {
                        if (dx == 0 && dz == 0) {
                            level.destroyBlock(p, true);
                        } else {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
                        }
                    }
                }
            }
        } finally {
            busy = was;
        }
        touch();
    }

    /**
     * Sizes every layer of the pole column containing {@code anyPos}: thick at the bottom, thin
     * at the top, and builds / removes the extra blocks of the wide layers.
     */
    public static void retaper(Level level, BlockPos anyPos) {
        if (level.isClientSide) {
            return;
        }
        BlockPos c0 = centerOf(level, anyPos);
        if (c0 == null) {
            return;
        }
        BlockPos bottom = c0;
        int guard = 0;
        while (guard++ < MAX_POLE && isCenter(level, bottom.below())) {
            bottom = bottom.below();
        }
        int h = 1;
        while (h < MAX_POLE && isCenter(level, bottom.above(h))) {
            h++;
        }
        int taperH = h <= 1 ? 0 : Math.max(1, Math.min(h - 1, (int) Math.round(TAPER_FRACTION * h)));
        double base = Math.min(MAX_WIDTH, TOP_WIDTH + TAPER_SLOPE * h);
        boolean was = busy;
        busy = true;
        try {
            for (int i = 0; i < h; i++) {
                BlockPos c = bottom.above(i);
                double wb;
                double wt;
                if (i >= taperH) {
                    wb = TOP_WIDTH;
                    wt = TOP_WIDTH;
                } else {
                    wb = TOP_WIDTH + (base - TOP_WIDTH) * (1.0D - (double) i / taperH);
                    wt = TOP_WIDTH + (base - TOP_WIDTH) * (1.0D - (double) (i + 1) / taperH);
                }
                int w = (int) Math.max(0, Math.min(38, Math.round(((wb + wt) / 2.0D - 4.0D) / 2.0D)));
                BlockState cs = level.getBlockState(c);
                if (cs.getValue(WindTurbinePoleBlock.WIDTH) != w) {
                    level.setBlock(c, cs.setValue(WindTurbinePoleBlock.WIDTH, w), PLACE_FLAGS);
                    cs = level.getBlockState(c);
                }
                if (level.getBlockEntity(c) instanceof WindTurbinePoleBlockEntity be) {
                    be.setWidths((float) wb, (float) wt);
                }
                double width = WindTurbinePoleBlock.width(w);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos p = c.offset(dx, 0, dz);
                        BlockState cur = level.getBlockState(p);
                        boolean own = cur.getBlock() == cs.getBlock()
                                && cur.getValue(WindTurbinePoleBlock.OX) == dx + 2 && cur.getValue(WindTurbinePoleBlock.OZ) == dz + 2;
                        boolean needed = WindTurbinePoleBlock.covers(dx, width) && WindTurbinePoleBlock.covers(dz, width);
                        if (needed) {
                            BlockState want = cs.getBlock().defaultBlockState()
                                    .setValue(WindTurbinePoleBlock.WIDTH, w)
                                    .setValue(WindTurbinePoleBlock.OX, dx + 2)
                                    .setValue(WindTurbinePoleBlock.OZ, dz + 2);
                            if (own ? cur != want : (cur.isAir() || cur.canBeReplaced())) {
                                level.setBlock(p, want, PLACE_FLAGS);
                            }
                        } else if (own) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
                        }
                    }
                }
            }
        } finally {
            busy = was;
        }
        touch();
    }

    /** Re-evaluates the electric networks around a placed / removed tower block. */
    public static void refresh(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        touch();
        Set<BlockPos> targets = new LinkedHashSet<>();
        targets.add(pos);
        List<BlockPos> all = new ArrayList<>(members(level, pos));
        all.addAll(members(level, pos.above()));
        all.addAll(members(level, pos.below()));
        outer:
        for (BlockPos m : all) {
            for (Direction d : Direction.values()) {
                BlockPos n = m.relative(d);
                Block nb = level.getBlockState(n).getBlock();
                if (nb instanceof CableBlock || nb instanceof StandBlock) {
                    targets.add(n);
                    if (targets.size() >= 32) {
                        break outer;
                    }
                }
            }
        }
        for (BlockPos p : targets) {
            ElectricNetwork.updateAt(level, p);
        }
    }
}
