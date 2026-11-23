package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.fraeric.elnr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inventory of the street lamp base: one bulb and one plugged cable.
 */
public class StreetLampBlockEntity extends BlockEntity implements BulbHolder {

    public static final int BULB_SLOT = 0;
    public static final int CABLE_SLOT = 1;
    /** Container slots of the bulbs (the chandelier uses all six, the lamps only the first). */
    public static final int[] BULB_SLOTS = {0, 2, 3, 4, 5, 6};
    public static final int SLOTS = 7;

    private final SimpleContainer inventory = new SimpleContainer(SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == CABLE_SLOT) {
                return stack.getItem() instanceof CableItem;
            }
            return stack.getItem() instanceof BulbItem;
        }
    };

    public StreetLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STREET_LAMP.get(), pos, state);
    }

    /** Keeps the client copy (bulb and cable) in step: the cable renderer needs it to draw the connection. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public Container getInventory() {
        return inventory;
    }

    @Override
    public int bulbCount() {
        return getBlockState().getBlock() instanceof ChandelierBlock ? BULB_SLOTS.length : 1;
    }

    @Override
    public ItemStack bulbAt(int index) {
        return inventory.getItem(BULB_SLOTS[index]);
    }

    @Override
    public void setBulbAt(int index, ItemStack stack) {
        inventory.setItem(BULB_SLOTS[index], stack);
        setChanged();
    }

    @Override
    public BlockPos effectPos(BlockPos pos) {
        return getBlockState().getBlock() instanceof StreetLampBlock lamp
                && !(lamp instanceof WallStreetLampBlock) && !(lamp instanceof ChandelierBlock) ? pos.above(2) : pos;
    }

    /** The first bulb that is present (any kind), or empty. */
    @Override
    public ItemStack getBulb() {
        for (int i = 0; i < bulbCount(); i++) {
            if (!bulbAt(i).isEmpty()) {
                return bulbAt(i);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setBulb(ItemStack stack) {
        inventory.setItem(BULB_SLOT, stack);
        setChanged();
    }

    @Override
    public boolean hasBulb() {
        return !getBulb().isEmpty();
    }

    @Override
    public ItemStack getCable() {
        return inventory.getItem(CABLE_SLOT);
    }

    @Override
    public void setCable(ItemStack stack) {
        inventory.setItem(CABLE_SLOT, stack);
        setChanged();
    }

    @Override
    public boolean hasCable() {
        return !getCable().isEmpty();
    }

    @Override
    public void breakBulb(Level level, BlockPos pos) {
        setBulb(ItemStack.EMPTY);
        // The bulb burns in the lamp head, two blocks above the base.
        BlockPos headPos = getBlockState().getBlock() instanceof WallStreetLampBlock ? pos : pos.above(2);
        level.playSound(null, headPos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    headPos.getX() + 0.5D, headPos.getY() + 0.7D, headPos.getZ() + 0.5D,
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
