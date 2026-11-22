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
        int x = 0;
        int y = 0;
        switch (facing) {
            case DOWN -> x = 180;
            case NORTH -> x = 90;
            case EAST -> { x = 90; y = 90; }
            case SOUTH -> { x = 90; y = 180; }
            case WEST -> { x = 90; y = 270; }
            default -> { }
        }
        Font font = Minecraft.getInstance().font;
        String text = format(meter.getValue(), block.getKind().unit);
        float width = font.width(text);
        float scale = Math.min(0.022F, 0.66F / Math.max(1.0F, width));

        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        // Same rotation the blockstate applies to the model (y first, then x).
        pose.mulPose(Axis.YP.rotationDegrees(-y));
        pose.mulPose(Axis.XP.rotationDegrees(-x));
        pose.translate(-0.5D, -0.5D, -0.5D);
        // Onto the top face of the floor-authored panel, text facing up.
        pose.translate(0.5D, (MeterBlock.DEPTH + 0.05D) / 16.0D, 0.5D);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -width / 2.0F, -font.lineHeight / 2.0F + 1.0F, 0x55FF55, false,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
