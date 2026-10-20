package ports.buildcraft.transport;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableItem;

/**
 * Upstream {@code BlockPipeHolder} for a single pipe type; each face's connection is block state, and
 * the pipe's pluggables add their boxes to its shape.
 */
public class PipeBlock extends BaseEntityBlock {
    public static final Map<Direction, EnumProperty<PipeSide>> SIDES = new EnumMap<>(Direction.class);
    private static final double HIT_EPSILON = 1.0e-4;
    /** Further than any player reaches; the pick only has to land on the pipe being broken. */
    private static final double PICK_RANGE = 8;
    private static final VoxelShape CENTER = Block.box(4, 4, 4, 12, 12, 12);
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (Direction face : Direction.values()) SIDES.put(face, EnumProperty.create(face.getSerializedName(), PipeSide.class));
        VoxelShape[] arms = {
            Block.box(4, 0, 4, 12, 4, 12), Block.box(4, 12, 4, 12, 16, 12),
            Block.box(4, 4, 0, 12, 12, 4), Block.box(4, 4, 12, 12, 12, 16),
            Block.box(0, 4, 4, 4, 12, 12), Block.box(12, 4, 4, 16, 12, 12)
        };
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CENTER;
            for (int i = 0; i < 6; i++) {
                if ((mask & (1 << i)) != 0) shape = Shapes.or(shape, arms[i]);
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    public final PipeDefinition definition;

    public PipeBlock(Properties properties, PipeDefinition definition) {
        super(properties);
        this.definition = definition;
        BlockState state = stateDefinition.any();
        for (EnumProperty<PipeSide> side : SIDES.values()) state = state.setValue(side, PipeSide.NONE);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction face : Direction.values()) builder.add(SIDES.get(face));
    }

    public static boolean isConnected(BlockState state, Direction face) {
        return state.getBlock() instanceof PipeBlock && state.getValue(SIDES.get(face)).isConnected();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction face : Direction.values()) {
            if (state.getValue(SIDES.get(face)).isConnected()) mask |= 1 << face.ordinal();
        }
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && !pipe.pluggableShape().isEmpty()) {
            return pipe.shapeWith(SHAPES[mask]);
        }
        return SHAPES[mask];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BCTransport.PIPE_TYPE.get()) return null;
        return level.isClientSide
                ? (lvl, pos, st, be) -> ((PipeBlockEntity) be).clientTick()
                : (lvl, pos, st, be) -> ((PipeBlockEntity) be).serverTick();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) pipe.markForUpdate();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        Direction plugSide = pluggableAt(pipe, hit.getLocation());
        if (plugSide != null) {
            Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            return pipe.getPluggable(plugSide).onActivate(player, hand, local);
        }
        Direction part = partHit(state, pos, hit.getLocation());
        Direction side = part != null ? part : hit.getDirection();
        if (held.getItem() instanceof PluggableItem item && pipe.getPluggable(side) == null) {
            PipePluggable plug = item.onPlace(held, pipe, side, player, hand);
            if (plug == null) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            pipe.replacePluggable(side, plug);
            plug.onPlacedBy(player);
            if (!player.getAbilities().instabuild) held.shrink(1);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return definition.hasMenu || Wrenches.isWrench(held) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return pipe.behaviour().onUse((ServerPlayer) player, hand, part);
    }

    /** The face whose pluggable contains {@code location}, a point on the pipe's shape, or null if it is on the pipe itself. */
    @Nullable
    public static Direction pluggableAt(PipeBlockEntity pipe, Vec3 location) {
        BlockPos pos = pipe.getBlockPos();
        Vec3 local = location.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Direction face : Direction.values()) {
            PipePluggable plug = pipe.getPluggable(face);
            if (plug != null && plug.getBoundingBox().inflate(HIT_EPSILON).contains(local)) return face;
        }
        return null;
    }

    /**
     * Upstream {@code removedByPlayer}: breaking a pluggable takes off only that pluggable, which drops
     * unless the player is in creative. The game-mode mixins call this on both sides before a block is
     * destroyed and keep the pipe when it returns true. A creative player breaking the pipe itself
     * loses its pluggables, as upstream drops nothing in creative.
     */
    public static boolean onPlayerDestroy(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return false;
        HitResult hit = player.pick(PICK_RANGE, 1.0F, false);
        Direction side = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(pos)
                ? pluggableAt(pipe, hit.getLocation()) : null;
        if (level.isClientSide) return side != null;
        List<ItemStack> drops = new ArrayList<>();
        if (side != null) {
            pipe.removePluggable(side, drops);
        } else if (player.getAbilities().instabuild) {
            for (Direction face : Direction.values()) pipe.removePluggable(face, drops);
        }
        if (!player.getAbilities().instabuild) {
            for (ItemStack stack : drops) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        return side != null;
    }

    /** Upstream {@code EnumPipePart} of a hit: null for the centre, otherwise the clicked arm's face. */
    @Nullable
    static Direction partHit(BlockState state, BlockPos pos, Vec3 location) {
        double x = location.x - pos.getX() - 0.5;
        double y = location.y - pos.getY() - 0.5;
        double z = location.z - pos.getZ() - 0.5;
        double epsilon = 0.25 + HIT_EPSILON;
        if (Math.abs(x) <= epsilon && Math.abs(y) <= epsilon && Math.abs(z) <= epsilon) return null;
        Direction face = Direction.getNearest(x, y, z);
        return isConnected(state, face) ? face : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            List<ItemStack> drops = new ArrayList<>();
            pipe.flow().addDrops(drops);
            for (Direction face : Direction.values()) {
                PipePluggable plug = pipe.getPluggable(face);
                if (plug == null) continue;
                plug.onRemove();
                plug.addDrops(drops);
            }
            for (ItemStack stack : drops) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
