package net.fraeric.elnr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fraeric.elnr.common.cable.CableColors;
import net.fraeric.elnr.common.cable.CableTier;
import net.fraeric.elnr.common.cable.StandBlock;
import net.fraeric.elnr.common.cable.StandBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Draws the overhead lines of a cable stand as sagging ribbons.
 *
 * <p>Every line is stored at both of its stands. To make it independent of which end is
 * loaded first, each stand draws the half of the line that starts at itself: the line is
 * one curve from the stand with the lower position to the one with the higher position, and
 * the lower stand draws {@code t = 0..0.5}, the higher one {@code t = 0.5..1}.</p>
 */
public class StandBlockRenderer implements BlockEntityRenderer<StandBlockEntity> {

    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryBuild("elnr", "textures/block/cable.png"));
    private static final ResourceLocation TEXTURE_VHV = Objects.requireNonNull(
            ResourceLocation.tryBuild("elnr", "textures/block/cable_vhv.png"));
    /** Signal cable texture (64x32): black, white, black stripes; the body is sampled on the white stripe. */
    private static final ResourceLocation TEXTURE_SIGNAL = Objects.requireNonNull(
            ResourceLocation.tryBuild("elnr", "textures/block/cable_signal.png"));

    /** Sag of the line in the middle, as a fraction of its length (capped). */
    private static final double SAG_PER_LENGTH = 0.04D;
    private static final double MAX_SAG = 2.5D;

    public StandBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StandBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        BlockState state = entity.getBlockState();
        if (level == null || !(state.getBlock() instanceof StandBlock stand) || entity.getLinks().isEmpty()) {
            return;
        }

        CableTier tier = stand.getTier();
        float[] rgb = lineColor(stand);
        // Wire radius in blocks: grows with the voltage class; a bundle has a fat sheath.
        // A signal line is a thin wire (1 px) whatever its position in the enum.
        float radius = (tier.isSignal() ? 0.5F : 0.5F + 0.25F * tier.ordinal()) / 16.0F * (stand.isBundled() ? 2.4F : 1.0F);
        int sides = stand.isBundled() || (!tier.isSignal() && tier.ordinal() >= CableTier.HV.ordinal()) ? 8 : 6;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
                tier.isSignal() ? TEXTURE_SIGNAL : tier == CableTier.VHV ? TEXTURE_VHV : TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos());

        BlockPos here = entity.getBlockPos();
        Vec3 hereAnchor = StandBlock.anchor(state);
        for (BlockPos other : entity.getLinks()) {
            Vec3 otherAnchor = StandBlock.anchor(level.getBlockState(other))
                    .add(other.getX() - here.getX(), other.getY() - here.getY(), other.getZ() - here.getZ());
            boolean lower = here.asLong() < other.asLong();
            Vec3 from = lower ? hereAnchor : otherAnchor;
            Vec3 to = lower ? otherAnchor : hereAnchor;
            double length = from.distanceTo(to);
            // Heavier lines hang a little less.
            double sag = Math.min(length * SAG_PER_LENGTH * (stand.isBundled() ? 0.8D : 1.0D), MAX_SAG);
            int segments = Math.max(8, (int) Math.ceil(length * 3.0D));
            if ((segments & 1) == 1) {
                segments++;
            }
            int first = lower ? 0 : segments / 2;
            int last = lower ? segments / 2 : segments;
            // The two halves share the middle point, so the curve is closed.
            Vec3 previous = curve(from, to, sag, first / (double) segments);
            Vec3 previousUp = previous.add(0.0D, radius * 2.6D, 0.0D);
            for (int i = first + 1; i <= last; i++) {
                Vec3 next = curve(from, to, sag, i / (double) segments);
                tube(consumer, pose, light, previous, next, radius, sides, rgb[0], rgb[1], rgb[2]);
                if (stand.isBundled()) {
                    // Thin steel messenger wire riding on top of the sheath.
                    Vec3 nextUp = next.add(0.0D, radius * 2.6D, 0.0D);
                    tube(consumer, pose, light, previousUp, nextUp, radius * 0.35F, 4, 0.62F, 0.64F, 0.67F);
                    previousUp = nextUp;
                }
                previous = next;
            }
        }
    }

    /** Colour of the line: insulation colour, a dark sheath for bundles, tier metal for bare wire. */
    private static float[] lineColor(StandBlock stand) {
        int rgb;
        if (stand.isBundled()) {
            rgb = 0x2E2620;
        } else if (stand.getColor() >= 0) {
            rgb = CableColors.RGB[stand.getColor()];
        } else {
            rgb = stand.getTier().getColor();
        }
        return new float[]{((rgb >> 16) & 0xFF) / 255.0F * 0.85F,
                ((rgb >> 8) & 0xFF) / 255.0F * 0.85F, (rgb & 0xFF) / 255.0F * 0.85F};
    }

    private static Vec3 curve(Vec3 from, Vec3 to, double sag, double t) {
        return new Vec3(from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t - sag * 4.0D * t * (1.0D - t),
                from.z + (to.z - from.z) * t);
    }

    /** A round wire between two points: a prism with smooth radial normals. */
    private static void tube(VertexConsumer consumer, PoseStack.Pose pose, int light, Vec3 a, Vec3 b,
                             float radius, int sides, float red, float green, float blue) {
        Vec3 direction = b.subtract(a);
        if (direction.lengthSqr() < 1.0E-10D) {
            return;
        }
        direction = direction.normalize();
        Vec3 u = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (u.lengthSqr() < 1.0E-6D) {
            u = new Vec3(1.0D, 0.0D, 0.0D);
        }
        u = u.normalize();
        Vec3 v = direction.cross(u).normalize();
        for (int k = 0; k < sides; k++) {
            double a0 = Math.PI * 2.0D * k / sides;
            double a1 = Math.PI * 2.0D * (k + 1) / sides;
            Vec3 n0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 n1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            // Slight darkening of the lower half fakes shading, since the lines are not lit by normals alone.
            float s0 = 0.78F + 0.22F * (float) (n0.y * 0.5D + 0.5D);
            float s1 = 0.78F + 0.22F * (float) (n1.y * 0.5D + 0.5D);
            vertex(consumer, pose, light, a.add(n0.scale(radius)), n0, red * s0, green * s0, blue * s0);
            vertex(consumer, pose, light, a.add(n1.scale(radius)), n1, red * s1, green * s1, blue * s1);
            vertex(consumer, pose, light, b.add(n1.scale(radius)), n1, red * s1, green * s1, blue * s1);
            vertex(consumer, pose, light, b.add(n0.scale(radius)), n0, red * s0, green * s0, blue * s0);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, Vec3 p, Vec3 n,
                               float red, float green, float blue) {
        // The body of the cable texture is sampled around (0.25, 0.5), like the cable renderer.
        consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(red, green, blue, 1.0F)
                .uv(0.25F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF)
                .uv2(light)
                .normal(pose.normal(), (float) n.x, (float) n.y, (float) n.z)
                .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(StandBlockEntity entity) {
        return true;
    }
}
