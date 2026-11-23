package net.fraeric.elnr.common.machine;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of a tube light: a centre piece plus one arm per in-plane direction. The same boxes are
 * drawn by the renderer and used for the hitbox, so they always agree. Boxes are in block pixels (0..16);
 * pieces that bend around a corner reach a little outside the block.
 */
public final class TubeLightGeometry {

    public enum Part { HOUSING, GLOW, CAP }

    public record Box(double x0, double y0, double z0, double x1, double y1, double z1, Part part) {
    }

    public static final int NONE = 0;
    /** Runs to the edge of the block. */
    public static final int LINE = 1;
    /** Wraps around the outer edge of the support; A also draws the elbow. */
    public static final int CONVEX_A = 2;
    public static final int CONVEX_B = 3;
    /** Runs to the wall and climbs up it (inner corner). */
    public static final int CONCAVE = 4;
    /** Free end with a cap. */
    public static final int FREE = 5;

    /** Width in pixels of the glowing tube for the three thicknesses. */
    public static final double[] GLOW_WIDTH = {2.0D, 4.0D, 6.0D};

    private TubeLightGeometry() {
    }

    /** The four directions along the surface the tube lies on. */
    public static Direction[] planeDirs(Direction facing) {
        Direction[] out = new Direction[4];
        int i = 0;
        for (Direction d : Direction.values()) {
            if (d.getAxis() != facing.getAxis()) {
                out[i++] = d;
            }
        }
        return out;
    }

    public static int indexOf(Direction[] dirs, Direction d) {
        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i] == d) {
                return i;
            }
        }
        return -1;
    }

    public static List<Box> boxes(Direction f, int[] arms, TubeLightBlock.Kind kind, int thickness) {
        boolean fl = kind == TubeLightBlock.Kind.FLUORESCENT;
        double gw = GLOW_WIDTH[thickness];
        double hw = fl ? gw + 2.0D : gw + 1.0D;
        double hh = fl ? 1.0D : 0.5D;
        double gh = fl ? gw * 0.8D : 1.0D + gw * 0.4D;
        double top = hh + gh;
        double hx0 = 8.0D - hw / 2.0D, hx1 = 8.0D + hw / 2.0D;
        double gx0 = 8.0D - gw / 2.0D, gx1 = 8.0D + gw / 2.0D;
        Direction[] dirs = planeDirs(f);
        List<Box> out = new ArrayList<>();
        double hs = 8.0D + hw / 2.0D;   // arms start where the centre piece ends, so no faces overlap
        double gs = 8.0D + gw / 2.0D;
        double ge = hh * 0.5D;          // glow is sunk into the housing: no coplanar faces
        add(out, f, dirs[0], hx0, 0, hx0, hx1, hh, hx1, Part.HOUSING);
        add(out, f, dirs[0], gx0, ge, gx0, gx1, top, gx1, Part.GLOW);
        for (int i = 0; i < 4; i++) {
            Direction d = dirs[i];
            switch (arms[i]) {
                case LINE, CONVEX_A, CONVEX_B -> {
                    add(out, f, d, hx0, 0, hs, hx1, hh, 16.01D, Part.HOUSING);   // runs a hair into the neighbour: no visible seam
                    add(out, f, d, gx0, ge, gs, gx1, top, 16.01D, Part.GLOW);
                    if (arms[i] == CONVEX_A) {
                        add(out, f, d, gx0, ge, 16, gx1, top, 16 + top, Part.GLOW);
                    }
                }
                case CONCAVE -> {
                    add(out, f, d, hx0, 0, hs, hx1, hh, 16 - hh, Part.HOUSING);
                    add(out, f, d, gx0, ge, gs, gx1, top, 16 - top, Part.GLOW);
                    add(out, f, d, hx0, 0, 16 - hh, hx1, 16, 16, Part.HOUSING);
                    add(out, f, d, gx0, hh, 16 - top, gx1, 16, 16 - hh, Part.GLOW);
                }
                case FREE -> {
                    add(out, f, d, hx0, 0, hs, hx1, hh, 15, Part.HOUSING);
                    add(out, f, d, gx0, ge, gs, gx1, top, 15, Part.GLOW);
                    add(out, f, d, hx0, 0, 15, hx1, top, 16, Part.CAP);
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static void add(List<Box> out, Direction f, Direction d, double x0, double y0, double z0,
                            double x1, double y1, double z1, Part part) {
        Direction s = TubeLightBlock.cross(f, d);
        double[] a = world(f, d, s, x0, y0, z0);
        double[] b = world(f, d, s, x1, y1, z1);
        out.add(new Box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]), part));
    }

    /** Local frame: y = away from the support, z = along the arm, x = across. */
    private static double[] world(Direction f, Direction d, Direction s, double x, double y, double z) {
        return new double[]{
                8 - f.getStepX() * 8 + f.getStepX() * y + d.getStepX() * (z - 8) + s.getStepX() * (x - 8),
                8 - f.getStepY() * 8 + f.getStepY() * y + d.getStepY() * (z - 8) + s.getStepY() * (x - 8),
                8 - f.getStepZ() * 8 + f.getStepZ() * y + d.getStepZ() * (z - 8) + s.getStepZ() * (x - 8)};
    }
}
