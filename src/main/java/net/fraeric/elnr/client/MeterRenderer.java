package net.fraeric.elnr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fraeric.elnr.common.electric.MeterBlock;
import net.fraeric.elnr.common.electric.MeterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Draws the measured value on the display of a wall meter. */
public class MeterRenderer implements BlockEntityRenderer<MeterBlockEntity> {

    public MeterRenderer(BlockEntityRendererProvider.Context context) {
    }

    static String format(double value, String unit) {
        if (value >= 1000.0D) {
            return String.format(Locale.ROOT, "%.2fk%s", value / 1000.0D, unit);
        }
        if (unit.equals("A") && value < 100.0D) {
            return String.format(Locale.ROOT, "%.2f%s", value, unit);
        }
        return String.format(Locale.ROOT, "%.1f%s", value, unit);
    }

    @Override
    public void render(MeterBlockEntity meter, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = meter.getBlockState();
        if (!(state.getBlock() instanceof MeterBlock block)) {
            return;
        }
        Direction facing = state.getValue(MeterBlock.FACING);
        Direction up = MeterBlock.up(state);
        // Text frame: right x up = normal (the facing), so right = up x facing.
        net.minecraft.core.Vec3i right = up.getNormal().cross(facing.getNormal());
        net.minecraft.core.Vec3i n = facing.getNormal();
        net.minecraft.core.Vec3i u = up.getNormal();
        Font font = Minecraft.getInstance().font;
        String text = format(meter.getValue(), block.getKind().unit);
        float width = font.width(text);
        float scale = Math.min(0.022F, 0.66F / Math.max(1.0F, width));
        double h = -0.5D + (MeterBlock.DEPTH + 0.05D) / 16.0D;

        pose.pushPose();
        pose.translate(0.5D + n.getX() * h, 0.5D + n.getY() * h, 0.5D + n.getZ() * h);
        pose.mulPoseMatrix(new org.joml.Matrix4f(
                right.getX(), right.getY(), right.getZ(), 0.0F,
                u.getX(), u.getY(), u.getZ(), 0.0F,
                n.getX(), n.getY(), n.getZ(), 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F));
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -width / 2.0F, -font.lineHeight / 2.0F + 1.0F, 0x55FF55, false,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
