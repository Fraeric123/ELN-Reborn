package net.fraeric.elnr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.machine.TubeLightBlock;
import net.fraeric.elnr.common.machine.TubeLightBlockEntity;
import net.fraeric.elnr.common.machine.TubeLightGeometry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the boxes of a tube light; the glowing parts are fully bright while it is lit. */
public class TubeLightRenderer implements BlockEntityRenderer<TubeLightBlockEntity> {

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(ELNReborn.MODID, "textures/block/" + name + ".png");
    }

    private static final ResourceLocation HOUSING = tex("tube_housing");
    private static final ResourceLocation CAP = tex("tube_cap");
    private static final ResourceLocation FL_ON = tex("tube_fluorescent_on");
    private static final ResourceLocation FL_OFF = tex("tube_fluorescent_off");
    private static final ResourceLocation NEON_ON = tex("tube_neon_on");
    private static final ResourceLocation NEON_OFF = tex("tube_neon_off");

    public TubeLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TubeLightBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof TubeLightBlock block)) {
            return;
        }
        boolean lit = state.getValue(TubeLightBlock.LIT);
        boolean fluorescent = block.getKind() == TubeLightBlock.Kind.FLUORESCENT;
        ResourceLocation glowTex = fluorescent ? (lit ? FL_ON : FL_OFF) : (lit ? NEON_ON : NEON_OFF);
        int rgb = TubeLightBlock.rgbOf(state);
        int gr = rgb >> 16 & 255;
        int gg = rgb >> 8 & 255;
        int gb = rgb & 255;
        if (lit) {
            // full-strength colour: the brightest channel is pushed to 255
            int mx = Math.max(1, Math.max(gr, Math.max(gg, gb)));
            gr = gr * 255 / mx;
            gg = gg * 255 / mx;
            gb = gb * 255 / mx;
        } else {
            gr = gr / 2;
            gg = gg / 2;
            gb = gb / 2;
        }
        VertexConsumer housing = buffer.getBuffer(RenderType.entityCutoutNoCull(HOUSING));
        VertexConsumer cap = buffer.getBuffer(RenderType.entityCutoutNoCull(CAP));
        VertexConsumer glow = buffer.getBuffer(RenderType.entityCutoutNoCull(glowTex));
        int glowLight = lit ? LightTexture.pack(15, 15) : light;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (TubeLightGeometry.Box b : be.boxes()) {
            switch (b.part()) {
                case HOUSING -> box(housing, m, n, b, 255, 255, 255, light, false);
                case CAP -> box(cap, m, n, b, 255, 255, 255, light, false);
                case GLOW -> box(glow, m, n, b, gr, gg, gb, glowLight, lit);
            }
        }
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, TubeLightGeometry.Box b,
                            int r, int g, int bl, int light, boolean flat) {
        float x0 = (float) (b.x0() / 16.0D), y0 = (float) (b.y0() / 16.0D), z0 = (float) (b.z0() / 16.0D);
        float x1 = (float) (b.x1() / 16.0D), y1 = (float) (b.y1() / 16.0D), z1 = (float) (b.z1() / 16.0D);
        // up / down
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, bl, light);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, flat ? 1 : -1, 0, r, g, bl, light);
        // north / south
        quad(vc, m, n, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, flat ? 1 : 0, flat ? 0 : -1, r, g, bl, light);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, flat ? 1 : 0, flat ? 0 : 1, r, g, bl, light);
        // west / east
        quad(vc, m, n, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, flat ? 0 : -1, flat ? 1 : 0, 0, r, g, bl, light);
        quad(vc, m, n, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, flat ? 0 : 1, flat ? 1 : 0, 0, r, g, bl, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, int r, int g, int b, int light) {
        v(vc, m, n, ax, ay, az, 0, 1, nx, ny, nz, r, g, b, light);
        v(vc, m, n, bx, by, bz, 1, 1, nx, ny, nz, r, g, b, light);
        v(vc, m, n, cx, cy, cz, 1, 0, nx, ny, nz, r, g, b, light);
        v(vc, m, n, dx, dy, dz, 0, 0, nx, ny, nz, r, g, b, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv,
                          float nx, float ny, float nz, int r, int g, int b, int light) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
