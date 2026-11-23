package net.fraeric.elnr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.CableBlockEntity;
import net.fraeric.elnr.common.cable.CableTier;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * Draws surface mounted cables exactly like the original Electrical Age renderer.
 *
 * <p>The geometry itself lives in {@link CableGeometry}, a line by line port of
 * {@code CableRender.drawCable} / {@code drawNode}: every arm is a strip that runs from
 * the middle of the block to its edge, unconnected directions leave a short stub (which
 * makes the junction look like a plus sign), and corners, T junctions, crossings and
 * dead ends get the light coloured knob.</p>
 *
 * <p>This class only decides, per piece, which arms exist and how they join their partner
 * (the {@code CableRenderType} of the original), places the geometry on the mounting
 * face and feeds it to Minecraft's vertex buffer.</p>
 */
public class CableBlockRenderer implements BlockEntityRenderer<CableBlockEntity> {

    /** {@code sprites/cable.png} of the original (32x16): low, medium and high voltage. */
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryBuild("elnr", "textures/block/cable.png"));
    /** {@code sprites/cableVHV.png} of the original (64x32). */
    private static final ResourceLocation TEXTURE_VHV = Objects.requireNonNull(
            ResourceLocation.tryBuild("elnr", "textures/block/cable_vhv.png"));
    /**
     * Signal cable (64x32): the cable body is three pixels wide, black - white - black, the knobs
     * at junctions repeat the pattern. The white stripe takes the insulation colour of the cable.
     */
    private static final ResourceLocation TEXTURE_SIGNAL = Objects.requireNonNull(
            ResourceLocation.tryBuild("elnr", "textures/block/cable_signal.png"));

    /**
     * The original tints the cable with its dye colour, and an undyed cable uses dye 0,
     * a dark grey of 0.2. The knobs are always drawn white.
     */
    private static final float BODY_SHADE = 0.2F;
    /** Set to true to tint the cable body with the colour of its voltage tier instead. */
    private static final boolean TIER_TINT = false;

    public CableBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CableBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof CableBlock cableBlock) || blockEntity.getLevel() == null) {
            return;
        }

        int light = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos());
        CableTier tier = cableBlock.getTier();
        float bodyRed = BODY_SHADE;
        float bodyGreen = BODY_SHADE;
        float bodyBlue = BODY_SHADE;
        if (TIER_TINT) {
            bodyRed = ((tier.getColor() >> 16) & 0xFF) / 255.0F;
            bodyGreen = ((tier.getColor() >> 8) & 0xFF) / 255.0F;
            bodyBlue = (tier.getColor() & 0xFF) / 255.0F;
        }

        if (cableBlock.isBundled()) {
            bodyRed = 0.29F;
            bodyGreen = 0.23F;
            bodyBlue = 0.16F;
        } else if (cableBlock.getColor() >= 0) {
            int rgb = net.fraeric.elnr.common.cable.CableColors.RGB[cableBlock.getColor()];
            bodyRed = ((rgb >> 16) & 0xFF) / 255.0F * 0.8F;
            bodyGreen = ((rgb >> 8) & 0xFF) / 255.0F * 0.8F;
            bodyBlue = (rgb & 0xFF) / 255.0F * 0.8F;
        }

        if (tier.isSignal()) {
            // Black edges come from the texture; only the white stripe is tinted (a bare signal
            // cable stays white, a bundle gets a light grey stripe).
            bodyRed = 1.0F;
            bodyGreen = 1.0F;
            bodyBlue = 1.0F;
            if (cableBlock.isBundled()) {
                bodyRed = 0.75F;
                bodyGreen = 0.75F;
                bodyBlue = 0.75F;
            } else if (cableBlock.getColor() >= 0) {
                int rgb = net.fraeric.elnr.common.cable.CableColors.RGB[cableBlock.getColor()];
                bodyRed = ((rgb >> 16) & 0xFF) / 255.0F;
                bodyGreen = ((rgb >> 8) & 0xFF) / 255.0F;
                bodyBlue = (rgb & 0xFF) / 255.0F;
            }
        }

        ResourceLocation texture = tier.isSignal() ? TEXTURE_SIGNAL : tier == CableTier.VHV ? TEXTURE_VHV : TEXTURE;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();

        float widthPx = tier.getWidth() * 16.0F * (cableBlock.isBundled() ? 1.5F : 1.0F);
        float heightPx = tier.getHeight() * 16.0F;

        for (Direction face : Direction.values()) {
            if (!cableBlock.hasFace(state, face)) {
                continue;
            }
            List<CableBlock.Arm> arms = CableBlock.getCableArms(
                    blockEntity.getLevel(), blockEntity.getBlockPos(), state, face);

            boolean[] connected = new boolean[4];
            int[] method = new int[4];
            float[] endAt = new float[4];
            for (CableBlock.Arm arm : arms) {
                int index = armIndex(face, arm.dir());
                if (index < 0) {
                    continue;
                }
                connected[index] = true;
                switch (arm.kind()) {
                    // Same block: of the two cables meeting in a corner, one is shortened
                    // by the thickness of the other, the other runs all the way to the edge.
                    case INNER -> {
                        if (face.ordinal() > arm.partnerFace().ordinal()) {
                            method[index] = CableGeometry.INTERNAL;
                            endAt[index] = heightPx;
                        }
                    }
                    // Around an outer edge: one of the two cables overshoots by the
                    // thickness of the other so the corner is closed.
                    case EDGE -> {
                        if (face.ordinal() > arm.partnerFace().ordinal()) {
                            method[index] = CableGeometry.ETEND;
                            endAt[index] = heightPx;
                        }
                    }
                    // Into a device: the strip reaches the body of the device.
                    case DEVICE -> {
                        method[index] = CableGeometry.ETEND;
                        endAt[index] = arm.reach() * 16.0F;
                    }
                    default -> {
                    }
                }
            }

            Frame frame = new Frame(face);
            final float br = bodyRed;
            final float bg = bodyGreen;
            final float bb = bodyBlue;
            CableGeometry.QuadSink sink = (knob, nx, ny, nz, v0, v1, v2, v3) -> {
                float r = knob ? 1.0F : br;
                float g = knob ? 1.0F : bg;
                float b = knob ? 1.0F : bb;
                float wx = frame.normalX(nx, ny, nz);
                float wy = frame.normalY(nx, ny, nz);
                float wz = frame.normalZ(nx, ny, nz);
                vertex(pose, consumer, light, wx, wy, wz, frame, v0, r, g, b);
                vertex(pose, consumer, light, wx, wy, wz, frame, v1, r, g, b);
                vertex(pose, consumer, light, wx, wy, wz, frame, v2, r, g, b);
                vertex(pose, consumer, light, wx, wy, wz, frame, v3, r, g, b);
            };

            CableGeometry.drawCable(sink, widthPx, heightPx, connected, method, endAt);
            CableGeometry.drawNode(sink, widthPx, heightPx, connected);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, int light,
                               float nx, float ny, float nz, Frame frame, float[] v,
                               float red, float green, float blue) {
        consumer.vertex(pose.pose(), frame.worldX(v[0], v[1], v[2]),
                        frame.worldY(v[0], v[1], v[2]), frame.worldZ(v[0], v[1], v[2]))
                .color(red, green, blue, 1.0F)
                .uv(v[3], v[4])
                .overlayCoords(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }

    /** Index of the arm (Left, Right, Down, Up) that points along {@code dir}, or -1. */
    private static int armIndex(Direction face, Direction dir) {
        Direction z = zAxis(face);
        Direction y = yAxis(face);
        if (dir == z) {
            return CableGeometry.RIGHT;
        }
        if (dir == z.getOpposite()) {
            return CableGeometry.LEFT;
        }
        if (dir == y) {
            return CableGeometry.UP;
        }
        if (dir == y.getOpposite()) {
            return CableGeometry.DOWN;
        }
        return -1;
    }

    /** World direction of the local {@code +z} axis (the Right arm) on the given face. */
    private static Direction zAxis(Direction face) {
        return switch (face) {
            case DOWN, UP, NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }

    /** World direction of the local {@code +y} axis (the Up arm) on the given face. */
    private static Direction yAxis(Direction face) {
        return switch (face) {
            case DOWN -> Direction.SOUTH;
            case UP -> Direction.NORTH;
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
        };
    }

    /**
     * Maps the local cable space onto a block face. Local {@code x} (depth) grows from
     * the mounting surface towards the middle of the block, {@code y} and {@code z} lie
     * in the mounting plane around the block centre.
     */
    private static final class Frame {
        private final Direction face;
        private final Direction y;
        private final Direction z;

        Frame(Direction face) {
            this.face = face;
            this.y = yAxis(face);
            this.z = zAxis(face);
        }

        float worldX(float lx, float ly, float lz) {
            return 0.5F + face.getStepX() * (0.5F - lx) + y.getStepX() * ly + z.getStepX() * lz;
        }

        float worldY(float lx, float ly, float lz) {
            return 0.5F + face.getStepY() * (0.5F - lx) + y.getStepY() * ly + z.getStepY() * lz;
        }

        float worldZ(float lx, float ly, float lz) {
            return 0.5F + face.getStepZ() * (0.5F - lx) + y.getStepZ() * ly + z.getStepZ() * lz;
        }

        float normalX(float nx, float ny, float nz) {
            return -face.getStepX() * nx + y.getStepX() * ny + z.getStepX() * nz;
        }

        float normalY(float nx, float ny, float nz) {
            return -face.getStepY() * nx + y.getStepY() * ny + z.getStepY() * nz;
        }

        float normalZ(float nx, float ny, float nz) {
            return -face.getStepZ() * nx + y.getStepZ() * ny + z.getStepZ() * nz;
        }
    }
}
