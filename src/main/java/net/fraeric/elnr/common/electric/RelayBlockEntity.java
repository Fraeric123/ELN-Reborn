package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.CableBlock;
import net.fraeric.elnr.common.cable.CableTier;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModBlockEntities;
import net.fraeric.elnr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Logic of the relay: reads the voltage on the coil input, pulls the armature in and burns the coil
 * out when it is overdriven. Pull-in at 75 % of the coil voltage, release below 40 % (hysteresis),
 * the coil burns above 130 % (the usual voltage tolerance).
 */
public class RelayBlockEntity extends BlockEntity {

    public static final double PULL_IN = 0.75D;
    public static final double RELEASE = 0.4D;
    private static final int POLL_INTERVAL = 4;

    private final SimpleContainer container = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof RelayCoilItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    private boolean energised = false;
    private double measured = 0.0D;
    private int poll = 0;

    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
        container.addListener(c -> {
            poll = 0;
            setChanged();
        });
    }

    public Container getContainer() {
        return container;
    }

    public ItemStack getCoil() {
        return container.getItem(0);
    }

    public boolean isEnergised() {
        return energised;
    }

    /** @return the last measured coil voltage in volts. */
    public double getMeasured() {
        return measured;
    }

    /** @return the {@code coil} blockstate value of the inserted coil. */
    public int coilState() {
        ItemStack stack = container.getItem(0);
        return stack.getItem() instanceof RelayCoilItem coil ? coil.stateIndex() : 0;
    }

    private double readVoltage(Direction side, Direction mountFace) {
        if (level == null) {
            return 0.0D;
        }
        BlockPos cablePos = worldPosition.relative(side);
        BlockState cableState = level.getBlockState(cablePos);
        if (!(cableState.getBlock() instanceof CableBlock cable) || !cable.hasFace(cableState, mountFace)) {
            return 0.0D;
        }
        return Math.abs(ElectricNetwork.analyze(level,
                new ElectricNetwork.Node(cablePos.immutable(), mountFace)).voltage());
    }

    public void serverTick() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof RelayBlock)) {
            return;
        }
        if (--poll > 0) {
            return;
        }
        poll = POLL_INTERVAL;
        BlockState state = getBlockState();
        measured = readVoltage(RelayBlock.coilSide(state), SwitchBlock.mountFace(state));

        boolean wasEnergised = energised;
        if (container.getItem(0).getItem() instanceof RelayCoilItem coil && !coil.isBurnt()) {
            double rated = coil.getVoltage();
            if (measured > rated * CableTier.VOLTAGE_TOLERANCE) {
                burnOut();
                energised = false;
            } else if (!energised && measured >= rated * PULL_IN) {
                energised = true;
            } else if (energised && measured < rated * RELEASE) {
                energised = false;
            }
        } else {
            energised = false;
        }

        BlockState wanted = state.setValue(RelayBlock.COIL, coilState()).setValue(SwitchBlock.OPEN, energised);
        if (wanted != state) {
            level.setBlockAndUpdate(worldPosition, wanted);
            ElectricNetwork.updateAt(level, worldPosition);
            if (wasEnergised != energised) {
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F,
                        energised ? 0.6F : 0.5F);
            }
        }
    }

    private void burnOut() {
        container.setItem(0, new ItemStack(ModItems.RELAY_COIL_BURNT.get()));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, 8, 0.15D, 0.15D, 0.15D, 0.02D);
            serverLevel.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ItemStack coil = container.getItem(0);
        if (!coil.isEmpty()) {
            tag.put("Coil", coil.save(new CompoundTag()));
        }
        tag.putBoolean("Energised", energised);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        container.setItem(0, tag.contains("Coil") ? ItemStack.of(tag.getCompound("Coil")) : ItemStack.EMPTY);
        energised = tag.getBoolean("Energised");
    }
}
