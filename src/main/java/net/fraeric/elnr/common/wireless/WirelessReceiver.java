package net.fraeric.elnr.common.wireless;

import net.minecraft.world.level.block.entity.BlockEntity;

public interface WirelessReceiver {
    int getFrequency();

    /** A transmitter on this frequency is (not) in range and powered. */
    void setTxActive(boolean active);

    /** A remote control pulse on this frequency reached the receiver. */
    void pulse();

    default BlockEntity be() {
        return (BlockEntity) this;
    }
}
