package net.fraeric.elnr.common.cable;

import net.fraeric.elnr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the overhead lines of a {@link StandBlock}. Every line is stored at both of its
 * stands (each one knows the position of the other end); the renderer draws one half of the
 * line from each end.
 */
public class StandBlockEntity extends BlockEntity {

    private final List<BlockPos> links = new ArrayList<>();

    public StandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAND.get(), pos, state);
    }

    /** @return the stands this one is joined to by an overhead line, oldest first. */
    public List<BlockPos> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public boolean hasLink(BlockPos other) {
        return links.contains(other);
    }

    public void addLink(BlockPos other) {
        if (!links.contains(other)) {
            links.add(other.immutable());
            changed();
        }
    }

    public void removeLink(BlockPos other) {
        if (links.remove(other)) {
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Lines reach far beyond the block, so the renderer must not be culled with the block. */
    @Override
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition).inflate(1.0D);
        for (BlockPos link : links) {
            box = box.minmax(new AABB(link).inflate(1.0D));
        }
        return box;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        long[] packed = new long[links.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = links.get(i).asLong();
        }
        tag.putLongArray("Links", packed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        links.clear();
        for (long packed : tag.getLongArray("Links")) {
            links.add(BlockPos.of(packed));
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
}
