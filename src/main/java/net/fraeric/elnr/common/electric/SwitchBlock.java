package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two terminal switch, modelled on the original ELN {@code ElectricalSwitchElement}.
 *
 * <p>Like every ELN six-node element the switch lies flat on a support surface and
 * has a "front" axis. Its two terminals sit at both ends of that axis (the
 * original {@code front} and {@code front.inverse()} loads) and nothing else is
 * connected: a cable only joins the switch when it lies on the same surface and
 * runs into one of the two terminals. When the switch is closed, the cable networks
 * at its two terminals are merged.</p>
 *
 * <p>Like the original ELN switch, the block is rated: it melts above 1.25x its
 * current rating and explodes above 1.3x its rated voltage, even while it is open.
 * The small switches are rated 300 V / 15 A. See
 * {@link net.fraeric.elnr.common.cable.ElectricNetwork}.</p>
 */
public class SwitchBlock extends Block {

    /** Direction the switch points away from the surface it is mounted on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    /**
     * Quarter turns of the terminal axis around the mounting normal (the original's
     * {@code front}, changed in the original with a wrench).
     */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);

    /**
     * Boxes in the model frame: floor mounted ({@code y} = away from the support),
     * terminals along {@code z}. Units are 1/16 block. Must match the model JSON.
     */
    private static final double[] CASE = {6, 0, 4, 10, 1.5, 12};
    private static final double[] LEVER_OPEN = {7, 1.5, 5, 9, 3.5, 11};
    private static final double[] LEVER_CLOSED = {7, 1.5, 5, 9, 3.5, 11};
    private static final double[] PIN_A = {7, 0, 0, 9, 1, 4};
    private static final double[] PIN_B = {7, 0, 12, 9, 1, 16};

    /** Default rating of the small switches (light switch, selector switch, button). */
    public static final double DEFAULT_RATED_VOLTAGE = 300.0D;
    public static final double DEFAULT_RATED_CURRENT = 15.0D;

    private final double ratedVoltage;
    private final double ratedCurrent;

    public SwitchBlock(Properties properties) {
        this(properties, DEFAULT_RATED_VOLTAGE, DEFAULT_RATED_CURRENT);
    }

    public SwitchBlock(Properties properties, double ratedVoltage, double ratedCurrent) {
        super(properties);
        this.ratedVoltage = ratedVoltage;
        this.ratedCurrent = ratedCurrent;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(ROTATION, 0)
                .setValue(OPEN, Boolean.TRUE));
    }

    /** @return the rated voltage in volts; the switch explodes above 1.3x this value. */
    public double getRatedVoltage() {
        return ratedVoltage;
    }

    /** @return the rated current in amperes; the switch melts above 1.25x this value. */
    public double getRatedCurrent() {
        return ratedCurrent;
    }

    // ------------------------------------------------------------------
    // Orientation / terminals
    // ------------------------------------------------------------------

    /**
     * Direction of the first terminal for the given mounting direction and rotation.
     * Rotation 0 is NORTH on floors/ceilings and UP on walls; every further step
     * turns the axis 90 degrees around the mounting normal.
     */
    public static Direction terminalDirection(Direction facing, int rotation) {
        Direction t = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        for (int i = 0; i < (rotation & 3); i++) {
            t = cross(facing, t);
        }
        return t;
    }

    /** Cross product of two axis-aligned directions, as a direction. */
    static Direction cross(Direction a, Direction b) {
        int x = a.getStepY() * b.getStepZ() - a.getStepZ() * b.getStepY();
        int y = a.getStepZ() * b.getStepX() - a.getStepX() * b.getStepZ();
        int z = a.getStepX() * b.getStepY() - a.getStepY() * b.getStepX();
        return Direction.getNearest(x, y, z);
    }

    /** @return the terminal direction of this state (its opposite is the other terminal). */
    public static Direction terminal(BlockState state) {
        return terminalDirection(state.getValue(FACING), state.getValue(ROTATION));
    }

    /** @return true when {@code side} points from the switch towards one of its terminals. */
    public static boolean hasTerminal(BlockState state, Direction side) {
        Direction terminal = terminal(state);
        return side == terminal || side == terminal.getOpposite();
    }

    /**
     * @return the face value a cable piece needs to join this switch: the direction
     * from the cable towards its support, which is the support of the switch itself.
     */
    public static Direction mountFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    public boolean isOpen(BlockState state) {
        return state.getValue(OPEN);
    }

    /** @return the sides of the block (from its centre) on which it has a terminal. */
    public Direction[] terminalSides(BlockState state) {
        Direction t = terminal(state);
        return new Direction[]{t, t.getOpposite()};
    }

    public final boolean hasTerminalAt(BlockState state, Direction side) {
        for (Direction d : terminalSides(state)) {
            if (d == side) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param entered the side of the block whose terminal the current enters through
     * @return the sides whose terminals it is connected to right now (empty = open)
     */
    public Direction[] passThrough(BlockState state, Direction entered) {
        return isOpen(state) ? new Direction[0] : new Direction[]{entered.getOpposite()};
    }

    /** Boxes of the model (model frame, 1/16) for the hitbox. */
    protected double[][] boxes(BlockState state) {
        return new double[][]{CASE, state.getValue(OPEN) ? LEVER_OPEN : LEVER_CLOSED, PIN_A, PIN_B};
    }

    protected static final double[] BOX_CASE = CASE;
    protected static final double[] BOX_PIN_A = PIN_A;
    protected static final double[] BOX_PIN_B = PIN_B;

    /** What a right click does (toggle by default). */
    protected void activate(BlockState state, Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, state.cycle(OPEN));
        // Original ELN: "random.click" at volume 0.3 and pitch 0.6, both directions.
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.6F);
        ElectricNetwork.updateAt(level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        // Run the terminals along the player's view on floors and ceilings and along
        // the wall on walls, like the original which derives the front from the placer.
        Direction wanted = facing.getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection()
                : facing.getClockWise();
        int best = 0;
        int bestDot = Integer.MIN_VALUE;
        for (int rotation = 0; rotation < 4; rotation++) {
            Direction t = terminalDirection(facing, rotation);
            int dot = t.getStepX() * wanted.getStepX() + t.getStepY() * wanted.getStepY()
                    + t.getStepZ() * wanted.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = rotation;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(ROTATION, best);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION, OPEN);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes(state)) {
            shape = Shapes.or(shape, modelBox(state, box));
        }
        return shape;
    }

    /**
     * Transforms a box from the model frame into the block, with exactly the same
     * rotation the blockstate applies to the model: model Y is the facing, model Z is
     * the terminal direction and model X is facing x terminal.
     */
    private static VoxelShape modelBox(BlockState state, double[] box) {
        Direction facing = state.getValue(FACING);
        Direction terminal = terminal(state);
        Direction side = cross(facing, terminal);
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int corner = 0; corner < 8; corner++) {
            double mx = (corner & 1) == 0 ? box[0] : box[3];
            double my = (corner & 2) == 0 ? box[1] : box[4];
            double mz = (corner & 4) == 0 ? box[2] : box[5];
            double[] world = new double[3];
            // The support plane lies half a block behind the centre, opposite the facing.
            world[0] = 8 - facing.getStepX() * 8 + facing.getStepX() * my
                    + terminal.getStepX() * (mz - 8) + side.getStepX() * (mx - 8);
            world[1] = 8 - facing.getStepY() * 8 + facing.getStepY() * my
                    + terminal.getStepY() * (mz - 8) + side.getStepY() * (mx - 8);
            world[2] = 8 - facing.getStepZ() * 8 + facing.getStepZ() * my
                    + terminal.getStepZ() * (mz - 8) + side.getStepZ() * (mx - 8);
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], world[axis]);
                max[axis] = Math.max(max[axis], world[axis]);
            }
        }
        return Shapes.box(min[0] / 16.0D, min[1] / 16.0D, min[2] / 16.0D,
                max[0] / 16.0D, max[1] / 16.0D, max[2] / 16.0D);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Cables are laid against the sides of the device, not used to flip it.
        if (player.getItemInHand(hand).getItem() instanceof CableItem) {
            return InteractionResult.PASS;
        }
        // Sneak + empty hand turns the terminal axis (the original uses a wrench).
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, state.cycle(ROTATION));
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.9F);
                ElectricNetwork.updateAt(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            activate(state, level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, pos);
        }
    }
}
