package net.fraeric.elnr.common.machine;

import net.fraeric.elnr.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Capacitor or battery. The stored energy travels inside the dropped item. */
public class StorageBlock extends MachineBlock {

    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    private final boolean battery;
    private final double voltage;
    /** Farads for a capacitor, ampere-hours for a battery. */
    private final double capacity;

    public StorageBlock(Properties properties, boolean battery, double voltage, double capacity) {
        super(properties);
        this.battery = battery;
        this.voltage = voltage;
        this.capacity = capacity;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    public boolean isBattery() { return battery; }
    public double getVoltage() { return voltage; }
    public double getCapacity() { return capacity; }

    /** Maximum stored energy in joules. */
    public double maxEnergy() {
        return battery ? voltage * capacity * 3600.0D : 0.5D * capacity * voltage * voltage;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageBlockEntity(pos, state);
    }

    /** Right-click shows the terminal voltage, stored energy, charge level and the current power flow. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (isCable(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StorageBlockEntity be) {
            double max = maxEnergy();
            double watts = be.getLastWatts();
            String key = watts > 1.0D ? "charging" : watts < -1.0D ? "discharging" : "idle";
            String power = Math.abs(watts) >= 1.0E3D
                    ? String.format(java.util.Locale.ROOT, "%.1f kW", Math.abs(watts) / 1.0E3D)
                    : String.format(java.util.Locale.ROOT, "%.0f W", Math.abs(watts));
            player.displayClientMessage(Component.translatable("message.elnr.storage.info",
                    String.format(java.util.Locale.ROOT, "%.1f", be.storageVoltage()),
                    StorageBlockItem.fmt(be.getEnergy()), StorageBlockItem.fmt(max),
                    String.format(java.util.Locale.ROOT, "%.0f", max > 0 ? 100.0D * be.getEnergy() / max : 0.0D),
                    Component.translatable("message.elnr.storage." + key, power)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack stack = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof StorageBlockEntity be
                && be.getEnergy() > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Energy", be.getEnergy());
            stack.addTagElement("BlockEntityTag", tag);
        }
        return List.of(stack);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof StorageBlockEntity be && be.getEnergy() > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Energy", be.getEnergy());
            stack.addTagElement("BlockEntityTag", tag);
        }
        return stack;
    }
}
