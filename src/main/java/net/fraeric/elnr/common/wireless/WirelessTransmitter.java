package net.fraeric.elnr.common.wireless;

import net.minecraft.world.level.block.entity.BlockEntity;

public interface WirelessTransmitter {
    int getFrequency();

    boolean isTransmitting();

    default BlockEntity be() {
        return (BlockEntity) this;
    }
}
