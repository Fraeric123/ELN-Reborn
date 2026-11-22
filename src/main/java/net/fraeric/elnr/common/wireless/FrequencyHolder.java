package net.fraeric.elnr.common.wireless;

/** A block entity with a wireless frequency (0..9999). */
public interface FrequencyHolder {
    int MAX_FREQUENCY = 9999;

    int getFrequency();

    void setFrequency(int frequency);
}
