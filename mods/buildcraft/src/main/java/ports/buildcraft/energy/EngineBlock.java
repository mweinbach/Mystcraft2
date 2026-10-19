package ports.buildcraft.energy;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.core.Wrenches;

/** One block class for the redstone, Stirling and combustion engines. */
public class EngineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<PowerStage> STAGE = EnumProperty.create("stage", PowerStage.class);
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        for (Direction direction : Direction.values()) {
            SHAPES[direction.ordinal()] = Shapes.or(rotated(direction, 0, 0, 0, 16, 8, 16), rotated(direction, 4, 8, 4, 12, 16, 12));
        }
    }

    private final Supplier<? extends BlockEntityType<? extends EngineBlockEntity>> type;

    public EngineBlock(Properties properties, Supplier<? extends BlockEntityType<? extends EngineBlockEntity>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(STAGE, PowerStage.BLUE));
    }

    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case UP -> Block.box(x1, y1, z1, x2, y2, z2);
            case DOWN -> Block.box(x1, 16 - y2, z1, x2, 16 - y1, z2);
            case NORTH -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
            case SOUTH -> Block.box(x1, z1, y1, x2, z2, y2);
            case WEST -> Block.box(16 - y2, x1, z1, 16 - y1, x2, z2);
            case EAST -> Block.box(y1, x1, z1, y2, x2, z2);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (blockEntityType != type.get()) return null;
        return level.isClientSide
                ? (lvl, pos, st, be) -> ((EngineBlockEntity) be).clientTick()
                : (lvl, pos, st, be) -> ((EngineBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            engine.onPlaced();
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            engine.onNeighbourChanged();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EngineBlockEntity engine)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (Wrenches.isWrench(held)) {
            if (!level.isClientSide) engine.attemptRotation();
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return engine.hasMenu() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return engine.onActivated((ServerPlayer) player, hand, hit.getDirection());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            Containers.dropContents(level, pos, engine.drops());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
