package net.fraeric.elnr.common.electric;

import net.fraeric.elnr.common.item.BulbItem;
import net.fraeric.elnr.common.cable.CableItem;
import net.fraeric.elnr.common.cable.ElectricNetwork;
import net.fraeric.elnr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Tall street lamp built from three blocks (lower pole, middle pole, lamp head).
 *
 * <p>The base holds a bulb and a plugged cable just like a bulb socket, so the lamp
 * joins every cable network of the same tier that touches the base. Right-click any
 * part with a bulb and then with a cable to wire it up.</p>
 */
public class StreetLampBlock extends Block implements EntityBlock {

    public enum Part implements StringRepresentable {
        LOWER("lower"), MIDDLE("middle"), HEAD("head");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Only meaningful on the head: the lamp is burning. */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Only meaningful on the head (the base of wall lamps / chandeliers): a bulb is inserted, so the model shows it. */
    public static final BooleanProperty BULB = BooleanProperty.create("bulb");

    private static final VoxelShape LOWER_SHAPE = Shapes.or(
            part(4, 0, 4, 12, 2, 12), part(5, 2, 5, 11, 4, 11),
            part(6, 4, 6, 10, 6, 10), part(6, 6, 6, 10, 16, 10));
    private static final VoxelShape MIDDLE_SHAPE = part(6, 0, 6, 10, 16, 10);
    private static final VoxelShape HEAD_SHAPE = Shapes.or(
            part(6, 0, 6, 10, 5, 10),
            part(4, 5, 4, 12, 6, 12),
            part(4.5, 6, 4.5, 11.5, 13, 11.5),
            part(4, 13, 4, 12, 14, 12),
            part(5, 14, 5, 11, 15, 11),
            part(6.5, 15, 6.5, 9.5, 16, 9.5));

    public StreetLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PART, Part.LOWER)
                .setValue(LIT, Boolean.FALSE)
                .setValue(BULB, Boolean.FALSE));
    }

    private static VoxelShape part(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d = 1.0D / 16.0D;
        return Shapes.box(x1 * d, y1 * d, z1 * d, x2 * d, y2 * d, z2 * d);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only the base of the lamp stores the bulb and the cable.
        return state.getValue(PART) == Part.LOWER ? new StreetLampBlockEntity(pos, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, LIT, BULB);
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** @return the position of the lamp base (the block holding the inventory). */
    public static BlockPos basePos(BlockPos pos, BlockState state) {
        return switch (state.getValue(PART)) {
            case LOWER -> pos;
            case MIDDLE -> pos.below();
            case HEAD -> pos.below(2);
        };
    }

    public static void setHeadLit(Level level, BlockPos basePos, boolean lit) {
        BlockState baseState = level.getBlockState(basePos);
        if ((baseState.getBlock() instanceof WallStreetLampBlock || baseState.getBlock() instanceof ChandelierBlock)) {
            if (baseState.getValue(LIT) != lit) {
                level.setBlock(basePos, baseState.setValue(LIT, lit), Block.UPDATE_ALL);
            }
            return;
        }
        BlockPos headPos = basePos.above(2);
        BlockState head = level.getBlockState(headPos);
        if (head.getBlock() instanceof StreetLampBlock
                && head.getValue(PART) == Part.HEAD
                && head.getValue(LIT) != lit) {
            level.setBlock(headPos, head.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Mirrors "a bulb is inserted" to the visible part of the lamp (the head, or the base of wall lamps / chandeliers). */
    public static void setHeadBulb(Level level, BlockPos basePos, boolean bulb) {
        BlockState baseState = level.getBlockState(basePos);
        if (baseState.getBlock() instanceof WallStreetLampBlock || baseState.getBlock() instanceof ChandelierBlock) {
            if (baseState.getValue(BULB) != bulb) {
                level.setBlock(basePos, baseState.setValue(BULB, bulb), Block.UPDATE_ALL);
            }
            return;
        }
        BlockPos headPos = basePos.above(2);
        BlockState head = level.getBlockState(headPos);
        if (head.getBlock() instanceof StreetLampBlock
                && head.getValue(PART) == Part.HEAD
                && head.getValue(BULB) != bulb) {
            level.setBlock(headPos, head.setValue(BULB, bulb), Block.UPDATE_ALL);
        }
    }

    public static boolean isHeadLit(BlockGetter level, BlockPos basePos) {
        BlockState baseState = level.getBlockState(basePos);
        if ((baseState.getBlock() instanceof WallStreetLampBlock || baseState.getBlock() instanceof ChandelierBlock)) {
            return baseState.getValue(LIT);
        }
        BlockState head = level.getBlockState(basePos.above(2));
        return head.getBlock() instanceof StreetLampBlock
                && head.getValue(PART) == Part.HEAD
                && head.getValue(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        LevelReader level = context.getLevel();
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            if (!level.getBlockState(pos.above(i)).canBeReplaced()) {
                return null;
            }
        }
        return defaultBlockState()
                .setValue(PART, Part.LOWER)
                .setValue(LIT, Boolean.FALSE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(PART) == Part.LOWER) {
            level.setBlock(pos.above(), defaultBlockState()
                    .setValue(PART, Part.MIDDLE).setValue(LIT, Boolean.FALSE), Block.UPDATE_ALL);
            level.setBlock(pos.above(2), defaultBlockState()
                    .setValue(PART, Part.HEAD).setValue(LIT, Boolean.FALSE), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return switch (state.getValue(PART)) {
            case LOWER -> level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
            case MIDDLE -> isPart(level, pos.below(), Part.LOWER);
            case HEAD -> isPart(level, pos.below(), Part.MIDDLE);
        };
    }

    private static boolean isPart(LevelReader level, BlockPos pos, Part part) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof StreetLampBlock && state.getValue(PART) == part;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (level.isClientSide || this instanceof WallStreetLampBlock || this instanceof ChandelierBlock) {
            return;
        }
        // Breaking any part takes the whole lamp down (the clicked part itself drops
        // through its loot table, which drops the lamp item for every part).
        BlockPos basePos = basePos(pos, state);
        for (int i = 0; i < 3; i++) {
            BlockPos partPos = basePos.above(i);
            if (!partPos.equals(pos) && isPart(level, partPos, Part.values()[i])) {
                level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Structure partners are brought down by updateShape; here only the
            // inventory of the base and the network refresh are handled.
            if (state.getValue(PART) == Part.LOWER && level.getBlockEntity(pos) instanceof StreetLampBlockEntity lamp) {
                if (!level.isClientSide) {
                    Containers.dropContents(level, pos, lamp.getInventory());
                }
            }
            if (!level.isClientSide) {
                ElectricNetwork.updateAt(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case LOWER -> LOWER_SHAPE;
            case MIDDLE -> MIDDLE_SHAPE;
            case HEAD -> HEAD_SHAPE;
        };
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PART) == Part.HEAD && state.getValue(LIT) ? 15 : 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos basePos = basePos(pos, state);
        if (!(level.getBlockEntity(basePos) instanceof StreetLampBlockEntity lamp)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new StreetLampMenu(id, inventory, lamp),
                            net.minecraft.network.chat.Component.translatable(getDescriptionId())),
                    basePos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            ElectricNetwork.updateAt(level, basePos(pos, state));
        }
    }
}
