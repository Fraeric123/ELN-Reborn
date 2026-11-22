package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Holds the last measured value of a wall meter and syncs it to clients for the display. */
public class MeterBlockEntity extends BlockEntity {

    private double value = 0.0D;

    public MeterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METER.get(), pos, state);
    }

    public double getValue() {
        return value;
    }

    public void setValue(double newValue) {
        if (Math.abs(newValue - value) < 0.005D) {
            return;
        }
        value = newValue;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Value", value);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        value = tag.getDouble("Value");
    }
}
