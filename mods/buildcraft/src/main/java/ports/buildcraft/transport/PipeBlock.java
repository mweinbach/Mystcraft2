package ports.buildcraft.transport;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableItem;
import ports.buildcraft.transport.wire.WireBetween;
import ports.buildcraft.transport.wire.WireItem;
import ports.buildcraft.transport.wire.WireNode;
import ports.buildcraft.transport.wire.WirePart;

/**
 * Upstream {@code BlockPipeHolder} for a single pipe type; each face's connection is block state, and
 * the pipe's pluggables and wires add their boxes to its shape.
 */
public class PipeBlock extends BaseEntityBlock {
    public static final Map<Direction, EnumProperty<PipeSide>> SIDES = new EnumMap<>(Direction.class);
    /** Whether a pluggable that redstone connects to is on any face, which makes the pipe a signal source. */
    public static final BooleanProperty REDSTONE = BooleanProperty.create("redstone");
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
        registerDefaultState(state.setValue(REDSTONE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction face : Direction.values()) builder.add(SIDES.get(face));
        builder.add(REDSTONE);
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
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && !pipe.extraShape().isEmpty()) {
            return pipe.shapeWith(SHAPES[mask]);
        }
        return SHAPES[mask];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * A facade copies whether its block can hold a torch. 1.19 caches that from the shape unless the block is
     * dynamic, so only the copied block's sturdy faces are offered here.
     */
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return Shapes.empty();
        VoxelShape shape = Shapes.empty();
        for (Direction face : Direction.values()) {
            PipePluggable plug = pipe.getPluggable(face);
            if (plug != null && plug.isSideSolid()) shape = Shapes.or(shape, Shapes.create(plug.getBoundingBox()));
        }
        return shape;
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

    // Redstone. Upstream emitted the same strong and weak signal from each face, as its gates set it.

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(REDSTONE);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity pipe ? pipe.getRedstoneOutput(direction.getOpposite()) : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /**
     * Forge's {@code IForgeBlock.canConnectRedstone}: wire connects only through a face whose pluggable allows it.
     * Vanilla wire connects to any signal source, so on Fabric the wire's side check calls this through
     * {@code RedStoneWireBlockMixin} in the Fabric-only mixin config. {@code direction} points from the wire to the pipe.
     */
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (direction == null || !(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return false;
        PipePluggable plug = pipe.getPluggable(direction.getOpposite());
        return plug != null && plug.canConnectToRedstone(direction);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) pipe.markForUpdate();
    }

    /**
     * Upstream {@code TileBC_Neptune.onPlacedBy}: the placing player owns the pipe. A pipe a stripes pipe lays is
     * placed by its owner's fake player, so it has the same owner.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            pipe.setOwner(player.getGameProfile());
        }
    }

    /** Upstream {@code onEntityCollidedWithBlock}: the behaviour hears of every entity inside the pipe's block. */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) pipe.behaviour().onEntityCollide(entity);
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
        if (held.getItem() instanceof WireItem wire) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (placeWire(pipe, hit, wire.colour)) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                return InteractionResult.CONSUME;
            }
        }
        if (level.isClientSide) {
            return definition.hasMenu || Wrenches.isWrench(held) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return pipe.behaviour().onUse((ServerPlayer) player, hand, part);
    }

    /**
     * Upstream's wire placement in {@code onBlockActivated}: clicking a wire puts the new one on the next corner
     * across the clicked face, which may be the next pipe's; clicking anywhere else picks the corner nearest the
     * click. Placement advancements are not ported.
     */
    private static boolean placeWire(PipeBlockEntity pipe, BlockHitResult hit, DyeColor colour) {
        WireHit wireHit = wireAt(pipe, hit.getLocation());
        PipeBlockEntity attach = pipe;
        WirePart part;
        if (wireHit != null && wireHit.part() != null) {
            WireNode node = new WireNode(pipe.getBlockPos(), wireHit.part()).offset(hit.getDirection());
            part = node.part();
            if (!node.pos().equals(pipe.getBlockPos())) {
                attach = pipe.getLevel().getBlockEntity(node.pos()) instanceof PipeBlockEntity other ? other : null;
            }
        } else {
            Vec3 location = hit.getLocation();
            part = WirePart.get(fraction(location.x) > 0.5, fraction(location.y) > 0.5, fraction(location.z) > 0.5);
        }
        return attach != null && attach.wires().addPart(part, colour);
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    /** A wire corner or a length between corners that a point on the pipe's shape lies on. */
    public record WireHit(@Nullable WirePart part, @Nullable WireBetween between) {}

    /** Upstream's wire boxes in {@code rayTrace}: the corner or length at {@code location}, or null if it is on neither. */
    @Nullable
    public static WireHit wireAt(PipeBlockEntity pipe, Vec3 location) {
        BlockPos pos = pipe.getBlockPos();
        Vec3 local = location.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (WirePart part : pipe.wires().parts().keySet()) {
            if (part.boundingBox.inflate(HIT_EPSILON).contains(local)) return new WireHit(part, null);
        }
        for (WireBetween between : pipe.wires().betweens().keySet()) {
            if (between.boundingBox.inflate(HIT_EPSILON).contains(local)) return new WireHit(null, between);
        }
        return null;
    }

    /**
     * Upstream {@code removedByPlayer} on a wire: a corner drops its wire; a length along the centre takes off the
     * corners at both ends; a length to the next pipe takes off this pipe's corner.
     */
    private static List<ItemStack> removeWire(PipeBlockEntity pipe, WireHit hit) {
        List<ItemStack> drops = new ArrayList<>();
        if (hit.part() != null) {
            DyeColor colour = pipe.wires().removePart(hit.part());
            if (colour != null) drops.add(new ItemStack(BCTransport.wireItem(colour)));
        } else if (hit.between() != null) {
            WireBetween between = hit.between();
            DyeColor colour = pipe.wires().getColourOfPart(between.parts[0]);
            if (colour == null) return drops;
            drops.add(new ItemStack(BCTransport.wireItem(colour), between.to == null ? 2 : 1));
            if (between.to == null) pipe.wires().removeParts(List.of(between.parts));
            else pipe.wires().removePart(between.parts[0]);
        }
        return drops;
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

    /** The pluggable face or the wire a player is looking at on a pipe. */
    private record PartHit(@Nullable Direction side, @Nullable WireHit wire) {}

    @Nullable
    private static PartHit partHit(PipeBlockEntity pipe, BlockPos pos, Player player) {
        HitResult hit = player.pick(PICK_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK || !blockHit.getBlockPos().equals(pos)) return null;
        Direction side = pluggableAt(pipe, hit.getLocation());
        if (side != null) return new PartHit(side, null);
        WireHit wire = wireAt(pipe, hit.getLocation());
        return wire == null ? null : new PartHit(null, wire);
    }

    /**
     * The client half of {@link #onPlayerDestroy}: whether breaking the pipe at {@code pos} would take off only a
     * pluggable or a wire, so the client keeps the pipe instead of predicting it away.
     */
    public static boolean hitsPart(Level level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && partHit(pipe, pos, player) != null;
    }

    /**
     * Upstream {@code removedByPlayer}: breaking a pluggable (a facade is one) or a wire takes off only that, which
     * drops unless the player is in creative. The server's game-mode mixin calls this before a pipe is destroyed
     * and keeps the pipe when it returns true. Upstream ran inside Forge's block breaking, after its
     * {@code BreakEvent}; here the removal first passes {@link BlockProtection#breakPart}, so protection mods
     * see it on both loaders. When they refuse, nothing changes and this still returns true, keeping the pipe.
     */
    public static boolean onPlayerDestroy(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return false;
        PartHit hit = partHit(pipe, pos, player);
        if (hit == null) return false;
        BlockProtection.breakPart(level, pos, player, () -> {
            List<ItemStack> drops = new ArrayList<>();
            if (hit.side() != null) pipe.removePluggable(hit.side(), drops);
            else drops.addAll(removeWire(pipe, hit.wire()));
            if (!player.getAbilities().instabuild) {
                for (ItemStack stack : drops) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        });
        return true;
    }

    /**
     * A creative player breaking the pipe itself loses its pluggables and wires, as upstream drops nothing in
     * creative. Both loaders call this once their break events have allowed the break.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            List<ItemStack> discarded = new ArrayList<>();
            for (Direction face : Direction.values()) pipe.removePluggable(face, discarded);
            pipe.wires().removeAll();
        }
        super.playerWillDestroy(level, pos, state, player);
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
            for (DyeColor colour : pipe.wires().removeAll()) drops.add(new ItemStack(BCTransport.wireItem(colour)));
            for (ItemStack stack : drops) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
