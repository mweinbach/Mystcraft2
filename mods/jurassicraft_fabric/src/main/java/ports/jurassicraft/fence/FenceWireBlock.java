package ports.jurassicraft.fence;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;

/** Wires connect to poles and shock living entities when a nearby connected base is powered. */
public final class FenceWireBlock extends IronBarsBlock {
    private static final DamageSource SHOCK = new DamageSource("jurassicraft_shock");
    private static final int MAX_RADIUS = 8;
    public static final DirectionProperty UP = DirectionProperty.create("up", direction -> direction == Direction.DOWN || direction.getAxis().isHorizontal());
    public FenceWireBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(UP, Direction.DOWN)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UP);
    }

    static boolean connects(BlockState state) {
        return state.is(FenceRegistry.WIRE.get()) || state.is(FenceRegistry.POLE.get());
    }
    private static BlockState updated(LevelAccessor level, BlockPos pos, BlockState state) {
        boolean any = false;
        Direction up = Direction.DOWN;
        BlockPos base = findBase(level, pos, true);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            boolean connected = connects(level.getBlockState(neighbor)) || level.getBlockState(neighbor.below()).is(FenceRegistry.WIRE.get());
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connected);
            any |= connected;
            if (up == Direction.DOWN && base != null && connects(level.getBlockState(neighbor.above()))) {
                BlockPos otherBase = findBase(level, neighbor.above(), true);
                if (otherBase != null && otherBase.getY() > base.getY()) up = direction;
            }
        }
        if (!any) for (Direction direction : Direction.Plane.HORIZONTAL) state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), true);
        state = state.setValue(UP, up);
        return state;
    }
    private static void refresh(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceWireBlock)) return;
        BlockState updated = updated(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, 3);
    }
    static void scheduleNearby(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 1; dy++) {
            BlockPos neighbor = pos.relative(direction).offset(0, dy, 0);
            if (!level.hasChunkAt(neighbor)) continue;
            Block block = level.getBlockState(neighbor).getBlock();
            if (block instanceof FenceWireBlock || block instanceof FencePoleBlock) level.scheduleTick(neighbor, block, 1);
        }
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updated(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return updated(level, pos, state);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) { refresh(level, pos); scheduleNearby(level, pos); }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!level.isClientSide && !next.is(this)) scheduleNearby(level, pos);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { refresh(level, pos); }
    private static VoxelShape fenceShape(BlockState state) {
        boolean north = state.getValue(NORTH), south = state.getValue(SOUTH), west = state.getValue(WEST), east = state.getValue(EAST);
        if (!north && !south && !west && !east) north = south = west = east = true;
        return box(west ? 0 : 6.4, 0, north ? 0 : 6.4, east ? 16 : 9.6, 16, south ? 16 : 9.6);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return fenceShape(state); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return fenceShape(state); }

    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living && living.isAlive()
                && powered(level, pos)) {
            living.hurt(SHOCK, 1.0F);
            if (living instanceof DinosaurEntity dinosaur) dinosaur.onFenceShock();
            if (entity.tickCount % 10 == 0)
                level.playSound(null, living.getX(), living.getY(), living.getZ(), FenceRegistry.SHOCK.get(), SoundSource.BLOCKS, 0.25F, 1.0F);
        }
    }

    public boolean canClimb(LivingEntity entity) {
        return entity instanceof Player || entity instanceof DinosaurEntity dinosaur
                && (dinosaur.species() == Species.VELOCIRAPTOR || dinosaur.species() == Species.MICRORAPTOR);
    }

    /** Limit the traversal to loaded chunks and the original eight-block pole range. */
    static boolean powered(Level level, BlockPos wire) {
        BlockPos base = findBase(level, wire, false);
        if (base == null) return false;
        // A pole powers bases connected within eight horizontal blocks of that
        // pole, even if the path first moves away from the wire being checked.
        BlockPos origin = base;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(base);
        seen.add(base);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (within(current, origin, MAX_RADIUS) && level.getBlockState(current.above()).is(FenceRegistry.POLE.get())
                    && level.hasNeighborSignal(current) && reaches(level, current, origin)) return true;
            enqueueNeighbors(level, current, origin, MAX_RADIUS * 2, queue, seen);
        }
        return false;
    }

    @javax.annotation.Nullable private static BlockPos findBase(LevelReader level, BlockPos wire, boolean includePoles) {
        BlockPos pos = wire;
        while (pos.getY() >= level.getMinBuildHeight() && level.hasChunkAt(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(FenceRegistry.BASE.get())) return pos;
            if (!state.is(FenceRegistry.WIRE.get()) && !(includePoles && state.is(FenceRegistry.POLE.get()))) return null;
            pos = pos.below();
        }
        return null;
    }

    private static boolean reaches(Level level, BlockPos pole, BlockPos target) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(pole);
        seen.add(pole);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (current.equals(target)) return true;
            enqueueNeighbors(level, current, pole, MAX_RADIUS, queue, seen);
        }
        return false;
    }

    private static void enqueueNeighbors(Level level, BlockPos current, BlockPos center, int radius,
            ArrayDeque<BlockPos> queue, Set<BlockPos> seen) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = -1; dy <= 1; dy++) {
                // The legacy network permits same-height diagonals, and only
                // cardinal connections when the base changes height.
                if (dy != 0 && dx != 0 && dz != 0) continue;
                BlockPos next = current.offset(dx, dy, dz);
                if (within(next, center, radius) && !seen.contains(next) && level.hasChunkAt(next)
                        && level.getBlockState(next).is(FenceRegistry.BASE.get())) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
    }

    private static boolean within(BlockPos pos, BlockPos center, int radius) {
        int dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }
}
