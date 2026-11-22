package net.fraeric.elnr.common.electric;

import net.minecraft.world.level.block.state.BlockState;

/** Classic high voltage knife switch: porcelain insulators and a long lever that only opens or closes the line. */
public class KnifeSwitchBlock extends SwitchBlock {
    // Must match knife_switch_closed.json / knife_switch_open.json.
    private static final double[] BASE = {4, 0, 2, 12, 1.5, 14};
    private static final double[] PIN_A = {6.5, 0, 0, 9.5, 1.5, 2};
    private static final double[] PIN_B = {6.5, 0, 14, 9.5, 1.5, 16};
    private static final double[] TOWER_A = {6.5, 1.5, 2.5, 9.5, 5, 5.5};
    private static final double[] TOWER_B = {6.5, 1.5, 10.5, 9.5, 5, 13.5};
    private static final double[] LEVER_CLOSED = {6, 5, 3.5, 10, 9, 12.5};
    private static final double[] LEVER_OPEN = {6, 5, 3.4, 10, 12.5, 4.8};

    public KnifeSwitchBlock(Properties properties, double ratedVoltage, double ratedCurrent) {
        super(properties, ratedVoltage, ratedCurrent);
    }

    @Override
    protected double[][] boxes(BlockState state) {
        return new double[][]{BASE, PIN_A, PIN_B, TOWER_A, TOWER_B, state.getValue(OPEN) ? LEVER_OPEN : LEVER_CLOSED};
    }
}
