package net.fraeric.elnr.common.electric;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Helpers to orient small wall devices (sockets, switches) against floors, walls
 * and ceilings.
 *
 * <p>Boxes are always authored in the "lying on the floor" orientation (attachment
 * face = down, device pointing up) and are then rotated onto the attachment face.
 * The rotation mirrors the vanilla button/lever blockstate tables, so the matching
 * blockstate JSON files use x = 90 for walls and x = 180 for ceilings.</p>
 */
public final class DeviceShapes {

    private DeviceShapes() {
    }

    /**
     * Builds a shape from boxes given in 1/16 block units, defined for a device
     * mounted on the floor ({@code facing = up}).
     *
     * @param facing direction the device points away from its support
     * @param boxes  arrays of six numbers {x1, y1, z1, x2, y2, z2} in 1/16 units
     */
    public static VoxelShape device(Direction facing, double[]... boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            shape = Shapes.or(shape, transform(facing,
                    box[0], box[1], box[2], box[3], box[4], box[5]));
        }
        return shape;
    }

    /**
     * Maps a point given in the floor-authored frame (1/16 units, {@code y} = height above the
     * support) onto the same device mounted with the given facing. Same mapping as
     * {@link #device}; returns block-local coordinates in blocks (0..1).
     */
    public static net.minecraft.world.phys.Vec3 point(Direction facing, double x, double y, double z) {
        double d = 1.0D / 16.0D;
        return switch (facing) {
            case UP -> new net.minecraft.world.phys.Vec3(x * d, y * d, z * d);
            case DOWN -> new net.minecraft.world.phys.Vec3(x * d, (16.0D - y) * d, z * d);
            case NORTH -> new net.minecraft.world.phys.Vec3(x * d, z * d, (16.0D - y) * d);
            case SOUTH -> new net.minecraft.world.phys.Vec3(x * d, z * d, y * d);
            case WEST -> new net.minecraft.world.phys.Vec3((16.0D - y) * d, z * d, x * d);
            case EAST -> new net.minecraft.world.phys.Vec3(y * d, z * d, x * d);
        };
    }

    private static VoxelShape transform(Direction facing,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2) {
        double d = 1.0D / 16.0D;
        return switch (facing) {
            // Floor: as authored. Ceiling: mirrored vertically.
            case UP -> Shapes.box(x1 * d, y1 * d, z1 * d, x2 * d, y2 * d, z2 * d);
            case DOWN -> Shapes.box(x1 * d, (16.0D - y2) * d, z1 * d,
                    x2 * d, (16.0D - y1) * d, z2 * d);
            // Walls: depth (y) becomes the horizontal axis towards the support,
            // the authored width (z) becomes the vertical extent.
            // The support lies behind the device, i.e. on the side opposite the facing.
            case NORTH -> Shapes.box(x1 * d, z1 * d, (16.0D - y2) * d,
                    x2 * d, z2 * d, (16.0D - y1) * d);
            case SOUTH -> Shapes.box(x1 * d, z1 * d, y1 * d, x2 * d, z2 * d, y2 * d);
            case WEST -> Shapes.box((16.0D - y2) * d, z1 * d, x1 * d,
                    (16.0D - y1) * d, z2 * d, x2 * d);
            case EAST -> Shapes.box(y1 * d, z1 * d, x1 * d, y2 * d, z2 * d, x2 * d);
        };
    }
}
