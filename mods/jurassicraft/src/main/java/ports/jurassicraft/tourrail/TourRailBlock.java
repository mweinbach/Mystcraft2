package ports.jurassicraft.tourrail;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Non-colliding rails recalculate their topology when nearby rails change. */
public final class TourRailBlock extends BaseEntityBlock {
    public static final EnumProperty<TourRailShape> SHAPE = EnumProperty.create("shape", TourRailShape.class);
    private static final VoxelShape FLAT = box(0,0,0,16,2,16), SLOPE = box(0,0,0,16,8,16);
    private static final int[][] NEIGHBORS = {{0,-1,TourRailLayout.N}, {0,1,TourRailLayout.S}, {1,0,TourRailLayout.E}, {-1,0,TourRailLayout.W},
            {1,-1,TourRailLayout.NE}, {-1,-1,TourRailLayout.NW}, {1,1,TourRailLayout.SE}, {-1,1,TourRailLayout.SW}};
    private final double speed;
    private final int color;
    public TourRailBlock(double speed, int color) {
        super(BlockBehaviour.Properties.of(Material.METAL).strength(1.0F).sound(SoundType.METAL).noCollission().noOcclusion());
        this.speed = speed;
        this.color = color;
        registerDefaultState(stateDefinition.any().setValue(SHAPE, TourRailShape.NORTH_SOUTH));
    }
    public double speed() { return speed; }
    public int stripeColor() { return color; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SHAPE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(SHAPE, context.getHorizontalDirection().getAxis() == Direction.Axis.X ? TourRailShape.EAST_WEST : TourRailShape.NORTH_SOUTH);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        TourRailShape shape = state.getValue(SHAPE);
        TourRailShape.End high = shape.first().rise() > 0 ? shape.first() : shape.second();
        if (!shape.ascending()) return true;
        BlockPos support = pos.offset(high.x(), 0, high.z());
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SHAPE).ascending() ? SLOPE : FLAT;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TourRailBlockEntity(pos, state); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(SHAPE, state.getValue(SHAPE).rotate(rotation)); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(SHAPE, state.getValue(SHAPE).mirror(mirror)); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) scheduleNearby(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()) {
            super.onRemove(state, level, pos, newState, moving);
            if (!level.isClientSide) scheduleNearby(level, pos);
        }
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos sourcePos, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    private static boolean rail(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof TourRailBlock;
    }
    private static void scheduleNearby(Level level, BlockPos pos) {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            BlockPos candidate = pos.offset(x,y,z);
            if (level.hasChunkAt(candidate) && level.getBlockState(candidate).getBlock() instanceof TourRailBlock rail)
                level.scheduleTick(candidate, rail, 1);
        }
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // A stale ascending shape may lose its high neighbor before topology updates.
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            level.destroyBlock(pos, true);
            return;
        }
        int mask = 0, rise = 0;
        for (int[] n : NEIGHBORS) {
            BlockPos same = pos.offset(n[0], 0, n[1]);
            boolean cardinal = n[0] == 0 || n[1] == 0;
            if (rail(level, same) || (cardinal && (rail(level, same.above()) || rail(level, same.below())))) mask |= n[2];
            if (cardinal && rail(level, same.above())) rise |= n[2];
        }
        TourRailShape next = TourRailLayout.choose(mask, rise, state.getValue(SHAPE));
        BlockState updated = state.setValue(SHAPE, next);
        if (!updated.canSurvive(level, pos)) { level.destroyBlock(pos, true); return; }
        if (updated != state) {
            level.setBlock(pos, updated, 3);
            scheduleNearby(level, pos);
        }
    }
}
