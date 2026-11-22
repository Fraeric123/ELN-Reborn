package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Slotless menu of the sound blocks; 4 synced values and button ids as commands. */
public class SpeakerMenu extends AbstractContainerMenu {

    public static final int BTN_REPEAT = 1;
    public static final int BTN_INTERRUPT = 2;
    public static final int BTN_STOP = 3;
    public static final int BTN_TEST = 100;
    public static final int BTN_SOUND = 10;

    private final BlockPos pos;
    private final SpeakerKind kind;
    @Nullable
    private final SpeakerBlockEntity entity;
    private final ContainerData data;

    /** Server side. */
    public SpeakerMenu(int id, Inventory inventory, SpeakerBlockEntity entity) {
        super(ModMenus.SPEAKER.get(), id);
        this.entity = entity;
        this.pos = entity.getBlockPos();
        this.kind = ((SpeakerBlock) entity.getBlockState().getBlock()).getKind();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> entity.getSoundIndex();
                    case 1 -> entity.isRepeat() ? 1 : 0;
                    case 2 -> entity.isInterrupt() ? 1 : 0;
                    default -> entity.isStopOnOff() ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        addDataSlots(data);
    }

    /** Client side. */
    public SpeakerMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.SPEAKER.get(), id);
        this.entity = null;
        this.pos = pos;
        this.kind = inventory.player.level().getBlockState(pos).getBlock() instanceof SpeakerBlock block
                ? block.getKind() : SpeakerKind.BELL;
        this.data = new SimpleContainerData(4);
        addDataSlots(data);
    }

    public SpeakerKind getKind() {
        return kind;
    }

    public int getSound() {
        return data.get(0);
    }

    public boolean isRepeat() {
        return data.get(1) != 0;
    }

    public boolean isInterrupt() {
        return data.get(2) != 0;
    }

    public boolean isStopOnOff() {
        return data.get(3) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || player.level().isClientSide) {
            return false;
        }
        if (id == BTN_REPEAT) {
            entity.toggleRepeat();
        } else if (id == BTN_INTERRUPT) {
            entity.toggleInterrupt();
        } else if (id == BTN_STOP) {
            entity.toggleStopOnOff();
        } else if (id == BTN_TEST) {
            if (entity.isInterrupt()) {
                entity.stopSound();
            }
            entity.play();
        } else if (id >= BTN_SOUND && id < BTN_SOUND + kind.sounds().length) {
            entity.setSoundIndex(id - BTN_SOUND);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
