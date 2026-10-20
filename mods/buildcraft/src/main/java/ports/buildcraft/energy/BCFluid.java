package ports.buildcraft.energy;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A BuildCraft oil-family fluid. Loader backends may subclass it to attach loader-only data (Forge
 * fluid types); all behaviour lives here so both loaders flow identically.
 *
 * <p>Gaseous stages flow up, as upstream's negative-density {@code BCFluidBlock} did under Forge 1.12's
 * {@code BlockFluidClassic}: vanilla's downward spreading is mirrored, so a gas rises in a column, spreads
 * under ceilings and seeks the nearest way up within the slope-find distance.
 */
public class BCFluid extends FlowingFluid {
    private final BCFluidDefinition definition;
    private final boolean source;

    public BCFluid(BCFluidDefinition definition, boolean source) {
        this.definition = definition;
        this.source = source;
        if (!source) registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
    }

    public BCFluidDefinition definition() { return definition; }

    @Override public Fluid getFlowing() { return definition.flowing; }
    @Override public Fluid getSource() { return definition.source; }
    @Override public Item getBucket() { return definition.bucket(); }

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        super.createFluidStateDefinition(builder);
        if (!source) builder.add(LEVEL);
    }

    /** Oil-family fluids never form infinite sources, as in upstream. */
    @Override protected boolean canConvertToSource() { return false; }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override protected int getSlopeFindDistance(LevelReader level) { return 4; }
    @Override protected int getDropOff(LevelReader level) { return definition.dropOff(); }
    @Override public int getTickDelay(LevelReader level) { return definition.tickDelay(); }
    @Override protected float getExplosionResistance() { return 100.0F; }

    private Direction flowDirection() {
        return definition.isGaseous() ? Direction.UP : Direction.DOWN;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == flowDirection() && !isSame(fluid);
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        LiquidBlock block = definition.block();
        return block.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override public boolean isSource(FluidState state) { return source; }
    @Override public int getAmount(FluidState state) { return source ? 8 : state.getValue(LEVEL); }
    @Override public boolean isSame(Fluid fluid) { return fluid == definition.source || fluid == definition.flowing; }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        if (!definition.isGaseous()) return super.getHeight(state, level, pos);
        return isSame(level.getFluidState(pos.below()).getType()) ? 1.0F : state.getOwnHeight();
    }

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        if (definition.isGaseous() && isSame(level.getFluidState(pos.below()).getType())) return Shapes.block();
        return super.getShape(state, level, pos);
    }

    @Override
    public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
        Vec3 flow = super.getFlow(level, pos, state);
        return definition.isGaseous() ? new Vec3(flow.x, -flow.y, flow.z) : flow;
    }

    @Override
    protected void spread(LevelAccessor level, BlockPos pos, FluidState state) {
        if (!definition.isGaseous()) {
            super.spread(level, pos, state);
            return;
        }
        if (state.isEmpty()) return;
        BlockState here = level.getBlockState(pos);
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        FluidState rising = getNewLiquid(level, above, aboveState);
        if (canSpreadTo(level, pos, here, Direction.UP, above, aboveState, level.getFluidState(above), rising.getType())) {
            spreadTo(level, above, aboveState, Direction.UP, rising);
            if (sourceNeighbourCount(level, pos) >= 3) spreadToSides(level, pos, state, here);
        } else if (state.isSource() || !isGasHole(level, rising.getType(), pos, here, above, aboveState)) {
            spreadToSides(level, pos, state, here);
        }
    }

    private void spreadToSides(LevelAccessor level, BlockPos pos, FluidState state, BlockState here) {
        int amount = state.getValue(FALLING) ? 7 : state.getAmount() - getDropOff(level);
        if (amount <= 0) return;
        for (Map.Entry<Direction, FluidState> entry : getSpread(level, pos, here).entrySet()) {
            BlockPos target = pos.relative(entry.getKey());
            BlockState targetState = level.getBlockState(target);
            if (canSpreadTo(level, pos, here, entry.getKey(), target, targetState, level.getFluidState(target), entry.getValue().getType())) {
                spreadTo(level, target, targetState, entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    protected FluidState getNewLiquid(LevelReader level, BlockPos pos, BlockState state) {
        if (!definition.isGaseous()) return super.getNewLiquid(level, pos, state);
        int highest = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(direction);
            BlockState neighbourState = level.getBlockState(neighbour);
            FluidState fluid = neighbourState.getFluidState();
            if (isSame(fluid.getType()) && canPassThroughWall(direction, level, pos, state, neighbour, neighbourState)) {
                highest = Math.max(highest, fluid.getAmount());
            }
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (isSame(belowState.getFluidState().getType()) && canPassThroughWall(Direction.DOWN, level, pos, state, below, belowState)) {
            return getFlowing(8, true);
        }
        int amount = highest - getDropOff(level);
        return amount <= 0 ? Fluids.EMPTY.defaultFluidState() : getFlowing(amount, false);
    }

    @Override
    protected Map<Direction, FluidState> getSpread(LevelReader level, BlockPos pos, BlockState state) {
        if (!definition.isGaseous()) return super.getSpread(level, pos, state);
        int shortest = 1000;
        Map<Direction, FluidState> spread = new EnumMap<>(Direction.class);
        Short2ObjectMap<Pair<BlockState, FluidState>> states = new Short2ObjectOpenHashMap<>();
        Short2BooleanMap holes = new Short2BooleanOpenHashMap();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(direction);
            short key = cacheKey(pos, neighbour);
            Pair<BlockState, FluidState> pair = cached(level, states, key, neighbour);
            FluidState newFluid = getNewLiquid(level, neighbour, pair.getFirst());
            if (!canPassThrough(level, newFluid.getType(), pos, state, direction, neighbour, pair.getFirst(), pair.getSecond())) continue;
            boolean hole = holes.computeIfAbsent(key, k -> isGasHole(level, getFlowing(), neighbour, pair.getFirst(), neighbour.above(), level.getBlockState(neighbour.above())));
            int distance = hole ? 0 : getSlopeDistance(level, neighbour, 1, direction.getOpposite(), pair.getFirst(), pos, states, holes);
            if (distance < shortest) spread.clear();
            if (distance <= shortest) {
                spread.put(direction, newFluid);
                shortest = distance;
            }
        }
        return spread;
    }

    @Override
    protected int getSlopeDistance(LevelReader level, BlockPos pos, int depth, Direction from, BlockState state, BlockPos origin,
                                   Short2ObjectMap<Pair<BlockState, FluidState>> states, Short2BooleanMap holes) {
        if (!definition.isGaseous()) return super.getSlopeDistance(level, pos, depth, from, state, origin, states, holes);
        int shortest = 1000;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction == from) continue;
            BlockPos neighbour = pos.relative(direction);
            short key = cacheKey(origin, neighbour);
            Pair<BlockState, FluidState> pair = cached(level, states, key, neighbour);
            if (!canPassThrough(level, getFlowing(), pos, state, direction, neighbour, pair.getFirst(), pair.getSecond())) continue;
            if (holes.computeIfAbsent(key, k -> isGasHole(level, getFlowing(), neighbour, pair.getFirst(), neighbour.above(), level.getBlockState(neighbour.above())))) {
                return depth;
            }
            if (depth < getSlopeFindDistance(level)) {
                shortest = Math.min(shortest, getSlopeDistance(level, neighbour, depth + 1, direction.getOpposite(), pair.getFirst(), origin, states, holes));
            }
        }
        return shortest;
    }

    private static Pair<BlockState, FluidState> cached(LevelReader level, Short2ObjectMap<Pair<BlockState, FluidState>> states, short key, BlockPos pos) {
        return states.computeIfAbsent(key, k -> {
            BlockState state = level.getBlockState(pos);
            return Pair.of(state, state.getFluidState());
        });
    }

    private static short cacheKey(BlockPos origin, BlockPos pos) {
        int x = pos.getX() - origin.getX();
        int z = pos.getZ() - origin.getZ();
        return (short) ((x + 128 & 0xFF) << 8 | z + 128 & 0xFF);
    }

    private boolean isGasHole(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, BlockPos above, BlockState aboveState) {
        if (!canPassThroughWall(Direction.UP, level, pos, state, above, aboveState)) return false;
        return isSame(aboveState.getFluidState().getType()) || canHoldFluid(level, above, aboveState, fluid);
    }

    private boolean canPassThrough(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, Direction direction,
                                   BlockPos neighbour, BlockState neighbourState, FluidState neighbourFluid) {
        return !(isSame(neighbourFluid.getType()) && neighbourFluid.isSource())
                && canPassThroughWall(direction, level, pos, state, neighbour, neighbourState)
                && canHoldFluid(level, neighbour, neighbourState, fluid);
    }

    private int sourceNeighbourCount(LevelReader level, BlockPos pos) {
        int sources = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            FluidState fluid = level.getFluidState(pos.relative(direction));
            if (isSame(fluid.getType()) && fluid.isSource()) sources++;
        }
        return sources;
    }

    private static boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos pos, BlockState state, BlockPos neighbour, BlockState neighbourState) {
        return !Shapes.mergedFaceOccludes(state.getCollisionShape(level, pos), neighbourState.getCollisionShape(level, neighbour), direction);
    }

    private static boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        Block block = state.getBlock();
        if (block instanceof LiquidBlockContainer container) return container.canPlaceLiquid(level, pos, state, fluid);
        if (block instanceof DoorBlock || state.is(BlockTags.SIGNS) || state.is(Blocks.LADDER) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BUBBLE_COLUMN)) {
            return false;
        }
        Material material = state.getMaterial();
        return material != Material.PORTAL && material != Material.STRUCTURAL_AIR && material != Material.WATER_PLANT
                && material != Material.REPLACEABLE_WATER_PLANT && !material.blocksMotion();
    }
}
