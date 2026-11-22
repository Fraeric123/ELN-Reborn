package net.fraeric.elnr.common.electric;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two way switch: one common terminal and two outputs. Terminals (relative to the
 * terminal axis {@code t} and {@code side = facing x t}): common on {@code -t},
 * output A on {@code +t}, output B on {@code +side}. {@code open=false} routes the
 * common terminal to A, {@code open=true} to B. The other output is left floating.
 */
public class SelectorSwitchBlock extends SwitchBlock {

    private static final double[] LEVER_A = {7, 1.5, 5, 9, 3.5, 11};
    private static final double[] LEVER_B = {7, 1.5, 5, 9, 3.5, 11};
    private static final double[] PIN_OUT_B = {10, 0, 7, 16, 1, 9};

    /** Big variant: must match {@code big_switch_a.json} / {@code big_switch_b.json}. */
    private static final double[] BIG_CASE = {3, 0, 3, 13, 1.5, 13};
    private static final double[] BIG_PIN_COMMON = {6.5, 0, 0, 9.5, 1.5, 3};
    private static final double[] BIG_PIN_A = {6.5, 0, 13, 9.5, 1.5, 16};
    private static final double[] BIG_PIN_B = {13, 0, 6.5, 16, 1.5, 9.5};
    private static final double[] BIG_TOWER_COMMON = {6.5, 1.5, 3.2, 9.5, 5, 5.7};
    private static final double[] BIG_TOWER_HUB = {6, 1.5, 6, 10, 5, 10};
    private static final double[] BIG_TOWER_A = {6.5, 1.5, 10.5, 9.5, 5, 14};
    private static final double[] BIG_TOWER_B = {10.5, 1.5, 6.5, 14, 5, 9.5};
    private static final double[] BIG_LEVER_A = {7, 5, 7.2, 9, 9, 13.5};
    private static final double[] BIG_LEVER_B = {7.2, 5, 7, 13.5, 9, 9};

    private final boolean big;

    public SelectorSwitchBlock(Properties properties) {
        this(properties, false, DEFAULT_RATED_VOLTAGE, DEFAULT_RATED_CURRENT);
    }

    public SelectorSwitchBlock(Properties properties, boolean big, double ratedVoltage, double ratedCurrent) {
        super(properties, ratedVoltage, ratedCurrent);
        this.big = big;
        registerDefaultState(defaultBlockState().setValue(OPEN, Boolean.FALSE));
    }

    public boolean isBig() {
        return big;
    }

    private static Direction sideOf(BlockState state) {
        return cross(state.getValue(FACING), terminal(state));
    }

    @Override
    public boolean isOpen(BlockState state) {
        return false;
    }

    @Override
    public Direction[] terminalSides(BlockState state) {
        Direction t = terminal(state);
        return new Direction[]{t.getOpposite(), t, sideOf(state)};
    }

    @Override
    public Direction[] passThrough(BlockState state, Direction entered) {
        Direction t = terminal(state);
        Direction active = state.getValue(OPEN) ? sideOf(state) : t;
        if (entered == t.getOpposite()) {
            return new Direction[]{active};
        }
        if (entered == active) {
            return new Direction[]{t.getOpposite()};
        }
        return new Direction[0];
    }

    @Override
    protected double[][] boxes(BlockState state) {
        if (big) {
            return new double[][]{BIG_CASE, BIG_PIN_COMMON, BIG_PIN_A, BIG_PIN_B, BIG_TOWER_COMMON,
                    BIG_TOWER_HUB, BIG_TOWER_A, BIG_TOWER_B, state.getValue(OPEN) ? BIG_LEVER_B : BIG_LEVER_A};
        }
        return new double[][]{BOX_CASE, state.getValue(OPEN) ? LEVER_B : LEVER_A,
                BOX_PIN_A, BOX_PIN_B, PIN_OUT_B};
    }
}
