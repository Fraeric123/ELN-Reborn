package net.fraeric.elnr.common.cable;

/**
 * Voltage classes of the mod's cables.
 *
 * <p>Values follow the original Electrical Age: rated voltage, nominal power and
 * the rendered cable cross section (in texture pixels, 16 px = 1 block).</p>
 */
public enum CableTier {

    LV("lv", 50, 20.0D, 1000, 1.95F, 0.95F, 0xC87137),
    MV("mv", 200, 10.0D, 2000, 2.95F, 0.95F, 0x9AA8BC),
    HV("hv", 800, 6.25D, 5000, 3.95F, 1.95F, 0xC03A3A),
    VHV("vhv", 3200, (15000.0D / 3200.0D), 15000, 3.95F, 1.95F, 0x8E5BC4),
    /**
     * Signal cable: carries only a true/false value (no voltage, no current), used to switch
     * relays. 3 px wide (black, white, black). Must stay the last constant.
     */
    SIGNAL("signal", 0, 0.0D, 0, 3.0F, 1.0F, 0x202020);

    /** Original ELN voltage watchdog triggers at 1.3x nominal voltage. */
    public static final double VOLTAGE_TOLERANCE = 1.3D;
    /** Original ELN power watchdog triggers at 1.2x nominal power. */
    public static final double POWER_TOLERANCE = 1.2D;
    /** Original ELN damage current is 1.25x the current rating (ElectricalSafety). */
    public static final double SWITCH_DAMAGE_CURRENT_FACTOR = 1.25D;

    private final String id;
    private final int voltage;
    private final double maxCurrent;
    private final int maxPower;
    private final float widthPixels;
    private final float heightPixels;
    private final int color;

    CableTier(String id, int voltage, double maxCurrent, int maxPower,
              float widthPixels, float heightPixels, int color) {
        this.id = id;
        this.voltage = voltage;
        this.maxCurrent = maxCurrent;
        this.maxPower = maxPower;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    /** @return true for the signal cable class, which only carries a boolean and never electricity. */
    public boolean isSignal() {
        return this == SIGNAL;
    }

    public int getVoltage() {
        return voltage;
    }

    public double getMaxCurrent() {
        return maxCurrent;
    }

    public int getMaxPower() {
        return maxPower;
    }

    /** Cable width in blocks (as rendered by the cable renderer). */
    public float getWidth() {
        return widthPixels / 16.0F;
    }

    /** Cable height above the mounting surface, in blocks. */
    public float getHeight() {
        return heightPixels / 16.0F;
    }

    /** Longest overhead line of this class, in blocks. */
    public double getMaxSpan() {
        return switch (this) {
            case LV -> 16.0D;
            case MV -> 24.0D;
            case HV -> 32.0D;
            case VHV -> 48.0D;
            case SIGNAL -> 32.0D;
        };
    }

    /** Highest voltage the insulation survives. */
    public double getBreakdownVoltage() {
        return voltage * VOLTAGE_TOLERANCE;
    }

    /** Highest current the conductor survives. */
    public double getBreakdownCurrent() {
        return maxCurrent * POWER_TOLERANCE;
    }

    /** RGB tint used by the cable renderer. */
    public int getColor() {
        return color;
    }
}
