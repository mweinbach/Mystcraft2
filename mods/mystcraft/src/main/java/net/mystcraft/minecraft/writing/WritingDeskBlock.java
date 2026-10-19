package net.mystcraft.minecraft.writing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.integration.registry.WritingDeskRegistry;

/** Original two adjacent base cells, with an optional matching pair of raised shelves. */
public final class WritingDeskBlock extends BaseEntityBlock {
    private static final ThreadLocal<Boolean> SILENT_PAIR_REMOVAL = ThreadLocal.withInitial(() -> false);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FOOT = BooleanProperty.create("foot"), TOP = BooleanProperty.create("top");
    public WritingDeskBlock() {
        super(Properties.of(Material.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FOOT, false).setValue(TOP, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, FOOT, TOP); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        if (!state.getValue(TOP)) return Block.box(0, 0, 0, 16, 16, 16);
        // Classic top outline occupies three quarters height and the back half of each cell.
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(8, 0, 0, 16, 12, 16);
            case WEST -> Block.box(0, 0, 8, 16, 12, 16);
            case NORTH -> Block.box(0, 0, 0, 8, 12, 16);
            default -> Block.box(0, 0, 0, 16, 12, 8);
        };
    }
    public static BlockPos headPosition(BlockPos position, BlockState state) {
        BlockPos base = state.getValue(TOP) ? position.below() : position;
        return state.getValue(FOOT) ? base.relative(state.getValue(FACING).getOpposite()) : base;
    }
    public static boolean matches(BlockState other, BlockState state, boolean foot, boolean top) {
        return other.getBlock() instanceof WritingDeskBlock && other.getValue(FACING) == state.getValue(FACING)
                && other.getValue(FOOT) == foot && other.getValue(TOP) == top;
    }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(FOOT) || state.getValue(TOP) ? null : new WritingDeskBlockEntity(position, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(FOOT) || state.getValue(TOP) ? null
                : createTickerHelper(type, WritingDeskRegistry.WRITING_DESK_ENTITY.get(), WritingDeskBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        // Let the extension item install its upper pair before the desk opens its screen.
        if (!state.getValue(TOP) && (player.getMainHandItem().is(WritingDeskRegistry.WRITING_DESK_EXTENSION.get())
                || player.getOffhandItem().is(WritingDeskRegistry.WRITING_DESK_EXTENSION.get()))) return InteractionResult.PASS;
        BlockPos head = headPosition(position, state);
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(head) instanceof WritingDeskBlockEntity desk)
            GameplayPlatform.openMenu(serverPlayer, desk, head);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos position, BlockState state) {
        return new ItemStack((state.getValue(TOP) ? WritingDeskRegistry.WRITING_DESK_EXTENSION : WritingDeskRegistry.WRITING_DESK_ITEM).get());
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos position, Block block, BlockPos neighbor, boolean moving) {
        if (level.isClientSide) return;
        BlockPos pair = position.relative(state.getValue(FOOT) ? state.getValue(FACING).getOpposite() : state.getValue(FACING));
        boolean connected = matches(level.getBlockState(pair), state, !state.getValue(FOOT), state.getValue(TOP));
        boolean supported = !state.getValue(TOP) || matches(level.getBlockState(position.below()), state, state.getValue(FOOT), false);
        if (!connected || !supported) level.destroyBlock(position, true);
    }
    @Override public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos head = headPosition(position, state);
            if (!state.getValue(TOP) && matches(level.getBlockState(head.above()), state, false, true)) {
                BlockPos topFoot = head.above().relative(state.getValue(FACING));
                clearSilently(level, topFoot);
                clearSilently(level, head.above());
            }
            if (state.getValue(FOOT)) {
                BlockPos pairedHead = position.relative(state.getValue(FACING).getOpposite());
                if (matches(level.getBlockState(pairedHead), state, false, state.getValue(TOP)))
                    clearSilently(level, pairedHead);
            }
        }
        super.playerWillDestroy(level, position, state, player);
    }
    private static void clearSilently(Level level, BlockPos position) {
        boolean previous = SILENT_PAIR_REMOVAL.get();
        SILENT_PAIR_REMOVAL.set(true);
        try { level.setBlock(position, Blocks.AIR.defaultBlockState(), 2); }
        finally { SILENT_PAIR_REMOVAL.set(previous); }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos position, BlockState next, boolean moving) {
        boolean changedPart = !state.is(next.getBlock()) || next.getValue(FOOT) != state.getValue(FOOT) || next.getValue(TOP) != state.getValue(TOP);
        if (changedPart && !level.isClientSide) {
            if (level.getBlockEntity(position) instanceof WritingDeskBlockEntity desk) desk.dropContents();
            BlockPos pair = position.relative(state.getValue(FOOT) ? state.getValue(FACING).getOpposite() : state.getValue(FACING));
            if (!SILENT_PAIR_REMOVAL.get() && matches(level.getBlockState(pair), state, !state.getValue(FOOT), state.getValue(TOP))) {
                if (state.getValue(FOOT)) level.destroyBlock(pair, true);
                else level.setBlock(pair, Blocks.AIR.defaultBlockState(), 3);
            }
            if (!SILENT_PAIR_REMOVAL.get() && !state.getValue(TOP) && matches(level.getBlockState(position.above()), state, state.getValue(FOOT), true))
                level.destroyBlock(position.above(), true);
            if (state.is(next.getBlock())) level.removeBlockEntity(position);
        }
        super.onRemove(state, level, position, next, moving);
    }
}
