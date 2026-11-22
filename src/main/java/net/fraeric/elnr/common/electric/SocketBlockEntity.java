package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.fraeric.elnr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;

public class SocketBlockEntity extends BlockEntity implements BulbHolder {

    public static final int BULB_SLOT = 0;
    public static final int CABLE_SLOT = 1;

    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == BULB_SLOT) {
                return stack.getItem() instanceof BulbItem;
            }
            return stack.getItem() instanceof CableItem;
        }
    };

    public SocketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOCKET.get(), pos, state);
    }

    public Container getInventory() {
        return inventory;
    }

    public ItemStack getBulb() {
        return inventory.getItem(BULB_SLOT);
    }

    public ItemStack getCable() {
        return inventory.getItem(CABLE_SLOT);
    }

    public boolean hasBulb() {
        return !getBulb().isEmpty();
    }

    public boolean hasCable() {
        return !getCable().isEmpty();
    }

    public void setBulb(ItemStack stack) {
        inventory.setItem(BULB_SLOT, stack);
        setChanged();
        syncBulbState();
        syncToClient();
    }

    public void setCable(ItemStack stack) {
        inventory.setItem(CABLE_SLOT, stack);
        setChanged();
        syncToClient();
    }

    /** Keeps the {@code bulb} blockstate property (used by the model) in step with the slot. */
    private void syncBulbState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof SocketBlock && state.getValue(SocketBlock.HAS_BULB) != hasBulb()) {
            BlockState updated = state.setValue(SocketBlock.HAS_BULB, hasBulb());
            if (!hasBulb()) {
                updated = updated.setValue(SocketBlock.LIT, false);
            }
            level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
        }
    }

    private void syncToClient() {
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

    public void breakBulb(Level level, BlockPos pos) {
        setBulb(ItemStack.EMPTY);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                    10, 0.25D, 0.25D, 0.25D, 0.05D);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                tag.put("Slot" + slot, stack.save(new CompoundTag()));
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            String key = "Slot" + slot;
            inventory.setItem(slot, tag.contains(key) ? ItemStack.of(tag.getCompound(key)) : ItemStack.EMPTY);
        }
    }
}
