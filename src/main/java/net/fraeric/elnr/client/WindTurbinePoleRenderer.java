package net.fraeric.elnr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.machine.WindTurbinePoleBlock;
import net.fraeric.elnr.common.machine.WindTurbinePoleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws one pole layer (however many blocks wide) as one tapering four-sided segment. */
public class WindTurbinePoleRenderer implements BlockEntityRenderer<WindTurbinePoleBlockEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ELNReborn.MODID, "textures/block/wind_turbine_pole.png");

    public WindTurbinePoleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public void render(WindTurbinePoleBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        float wb = be.getBottomWidth();
        float wt = be.getTopWidth();
        float hb = wb / 32.0F;
        float ht = wt / 32.0F;
        float ub = wb / 16.0F;
        float ut = wt / 16.0F;
        float off = (ub - ut) / 2.0F;
        float tilt = hb - ht;
        float len = (float) Math.sqrt(tilt * tilt + 1.0F);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90.0F * i));
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            float ny = tilt / len;
            float nz = -1.0F / len;
            vertex(vc, m, n, -hb, 0.0F, -hb, 0.0F, 1.0F, 0.0F, ny, nz, light);
            vertex(vc, m, n, hb, 0.0F, -hb, ub, 1.0F, 0.0F, ny, nz, light);
            vertex(vc, m, n, ht, 1.0F, -ht, off + ut, 0.0F, 0.0F, ny, nz, light);
            vertex(vc, m, n, -ht, 1.0F, -ht, off, 0.0F, 0.0F, ny, nz, light);
            pose.popPose();
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        if (!(level.getBlockState(be.getBlockPos().below()).getBlock() instanceof WindTurbinePoleBlock)) {
            vertex(vc, m, n, -hb, 0.0F, -hb, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, light);
            vertex(vc, m, n, hb, 0.0F, -hb, ub, 0.0F, 0.0F, -1.0F, 0.0F, light);
            vertex(vc, m, n, hb, 0.0F, hb, ub, ub, 0.0F, -1.0F, 0.0F, light);
            vertex(vc, m, n, -hb, 0.0F, hb, 0.0F, ub, 0.0F, -1.0F, 0.0F, light);
        }
        if (!(level.getBlockState(be.getBlockPos().above()).getBlock() instanceof WindTurbinePoleBlock)) {
            vertex(vc, m, n, -ht, 1.0F, -ht, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, light);
            vertex(vc, m, n, ht, 1.0F, -ht, ut, 0.0F, 0.0F, 1.0F, 0.0F, light);
            vertex(vc, m, n, ht, 1.0F, ht, ut, ut, 0.0F, 1.0F, 0.0F, light);
            vertex(vc, m, n, -ht, 1.0F, ht, 0.0F, ut, 0.0F, 1.0F, 0.0F, light);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
