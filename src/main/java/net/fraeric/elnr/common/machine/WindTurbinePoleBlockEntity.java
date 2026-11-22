package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Holds the bottom and top width of one pole layer; the renderer draws it as a tapering segment. */
public class WindTurbinePoleBlockEntity extends BlockEntity {

    private float bottomWidth = -1.0F;
    private float topWidth = -1.0F;

    public WindTurbinePoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.WIND_TURBINE_POLE_BE.get(), pos, state);
    }

    public void setWidths(float bottom, float top) {
        if (Math.abs(bottom - bottomWidth) < 0.001F && Math.abs(top - topWidth) < 0.001F) {
            return;
        }
        bottomWidth = bottom;
        topWidth = top;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Width in pixels of the bottom of the segment (falls back to the block state before it is known). */
    public float getBottomWidth() {
        return bottomWidth >= 0 ? bottomWidth : (float) WindTurbinePoleBlock.width(getBlockState().getValue(WindTurbinePoleBlock.WIDTH));
    }

    public float getTopWidth() {
        return topWidth >= 0 ? topWidth : getBottomWidth();
    }

    @Override
    public AABB getRenderBoundingBox() {
        double h = Math.max(getBottomWidth(), getTopWidth()) / 32.0D;
        return new AABB(worldPosition.getX() + 0.5D - h, worldPosition.getY(), worldPosition.getZ() + 0.5D - h,
                worldPosition.getX() + 0.5D + h, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D + h);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Bottom", bottomWidth);
        tag.putFloat("Top", topWidth);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bottomWidth = tag.contains("Bottom") ? tag.getFloat("Bottom") : -1.0F;
        topWidth = tag.contains("Top") ? tag.getFloat("Top") : -1.0F;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putFloat("Bottom", bottomWidth);
        tag.putFloat("Top", topWidth);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
