package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

public class MinerBlockEntity extends ElectricMachineBlockEntity {

    /** P = U^2 / R. */
    public static final double RESISTANCE = 700.0D;
    public static final double JOULES_PER_HARDNESS = 100.0D;

    private double progress;
    private final List<ItemStack> pending = new ArrayList<>();
    private final int breakerId = (int) (Math.random() * 1_000_000);

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.MINER_BE.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(MinerBlock.FACING);
    }

    private BlockPos target() {
        return worldPosition.relative(facing());
    }

    /** @return energy needed to mine the block in front, or -1 when there is nothing to mine. */
    private double targetCost() {
        if (level == null || !level.hasChunkAt(target())) {
            return -1.0D;
        }
        BlockState state = level.getBlockState(target());
        float hardness = state.getDestroySpeed(level, target());
        if (state.isAir() || hardness < 0 || !state.getFluidState().isEmpty()) {
            return -1.0D;
        }
        return JOULES_PER_HARDNESS * Math.max(hardness, 0.5D);
    }

    @Override
    public double demandWatts(double volts) {
        if (!pending.isEmpty() || volts <= 0 || targetCost() < 0) {
            return 0.0D;
        }
        return volts * volts / RESISTANCE;
    }

    @Override
    public void applyPower(double volts, double watts, double dt) {
        super.applyPower(volts, watts, dt);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double cost = targetCost();
        if (cost < 0 || watts <= 0 || !pending.isEmpty()) {
            if (progress != 0) {
                progress = 0;
                serverLevel.destroyBlockProgress(breakerId, target(), -1);
            }
            return;
        }
        progress += watts * dt;
        if (progress >= cost) {
            progress -= cost;
            mine(serverLevel);
            serverLevel.destroyBlockProgress(breakerId, target(), -1);
            if (cost < 0 || targetCost() < 0) {
                progress = 0;
            }
        } else {
            serverLevel.destroyBlockProgress(breakerId, target(), Math.min(9, (int) (progress / cost * 10.0D)));
        }
        setChanged();
    }

    private void mine(ServerLevel serverLevel) {
        BlockPos target = target();
        BlockState state = serverLevel.getBlockState(target);
        BlockEntity targetBe = serverLevel.getBlockEntity(target);
        List<ItemStack> drops = Block.getDrops(state, serverLevel, target, targetBe, null,
                new ItemStack(Items.NETHERITE_PICKAXE));
        serverLevel.destroyBlock(target, false);
        for (ItemStack drop : drops) {
            output(serverLevel, drop.copy(), target);
        }
    }

    private boolean hasInventory(ServerLevel serverLevel) {
        for (Direction dir : Direction.values()) {
            if (dir != facing() && serverLevel.getBlockEntity(worldPosition.relative(dir)) != null
                    && serverLevel.getBlockEntity(worldPosition.relative(dir))
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack insert(ServerLevel serverLevel, ItemStack stack) {
        for (Direction dir : Direction.values()) {
            if (dir == facing() || stack.isEmpty()) {
                continue;
            }
            BlockEntity neighbour = serverLevel.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null) {
                continue;
            }
            IItemHandler handler = neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).orElse(null);
            if (handler != null) {
                stack = ItemHandlerHelper.insertItem(handler, stack, false);
            }
        }
        return stack;
    }

    private void output(ServerLevel serverLevel, ItemStack stack, BlockPos dropAt) {
        if (!hasInventory(serverLevel)) {
            Block.popResource(serverLevel, dropAt, stack);
            return;
        }
        ItemStack rest = insert(serverLevel, stack);
        if (!rest.isEmpty()) {
            pending.add(rest);
        }
    }

    public void serverTick() {
        if (pending.isEmpty() || !(level instanceof ServerLevel serverLevel) || level.getGameTime() % 10 != 0) {
            return;
        }
        List<ItemStack> copy = new ArrayList<>(pending);
        pending.clear();
        for (ItemStack stack : copy) {
            ItemStack rest = insert(serverLevel, stack);
            if (!rest.isEmpty()) {
                pending.add(rest);
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Progress", progress);
        ListTag list = new ListTag();
        for (ItemStack stack : pending) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("Pending", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getDouble("Progress");
        pending.clear();
        for (Tag t : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            pending.add(ItemStack.of((CompoundTag) t));
        }
    }
}
