package net.fraeric.elnr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.machine.WindTurbineBlock;
import net.fraeric.elnr.common.machine.WindTurbineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the spinning three-blade rotor of the wind turbine. */
public class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineBlockEntity> {

    private static final ResourceLocation BLADE = new ResourceLocation(ELNReborn.MODID, "textures/block/wind_turbine_blade.png");

    public WindTurbineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WindTurbineBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (!(be.getBlockState().getBlock() instanceof WindTurbineBlock) || be.getLevel() == null) {
            return;
        }
        float angle = be.advanceAngle(be.getLevel().getGameTime() + partialTick);
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - be.getBlockState().getValue(WindTurbineBlock.FACING).toYRot()));
        pose.translate(0.0D, 0.0D, -0.47D);
        pose.mulPose(Axis.ZP.rotation(angle));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(BLADE));
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 120.0F));
            quad(vc, pose, light, overlay, (float) be.getBladeLength());
            pose.popPose();
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack pose, int light, int overlay, float length) {
        float halfWidth = 0.12F + 0.06F * length;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, -halfWidth, 0.06F, 0.0F, 0.0F, 1.0F, light, overlay);
        vertex(vc, m, n, halfWidth, 0.06F, 0.0F, 1.0F, 1.0F, light, overlay);
        vertex(vc, m, n, halfWidth, length, 0.0F, 1.0F, 0.0F, light, overlay);
        vertex(vc, m, n, -halfWidth, length, 0.0F, 0.0F, 0.0F, light, overlay);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float u, float v, int light, int overlay) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 0.0F, 1.0F).endVertex();
    }
}
