package ports.buildcraft.transport;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.lib.Injectable;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.PortHolder;
import ports.buildcraft.transport.behaviour.PipeBehaviour;
import ports.buildcraft.transport.flow.FluidFlow;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.flow.PipeFlow;
import ports.buildcraft.transport.flow.PowerFlow;
import ports.buildcraft.transport.flow.StructureFlow;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream {@code TilePipeHolder} and {@code Pipe}: owns one behaviour, one flow and a pluggable
 * on each face, and works out which faces connect to neighbouring pipes and blocks.
 */
public class PipeBlockEntity extends SyncedBlockEntity implements PortHolder, Injectable {
    public enum ConnectedType { PIPE, TILE }

    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    private final PipeDefinition definition;
    private final PipeBehaviour behaviour;
    private final PipeFlow flow;
    private final ConnectedType[] connections = new ConnectedType[6];
    private final PipePluggable[] pluggables = new PipePluggable[6];
    private volatile VoxelShape pluggableShape = Shapes.empty();
    @Nullable
    private volatile CombinedShape combinedShape;
    private boolean updateMarked = true;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(BCTransport.PIPE_TYPE.get(), pos, state);
        definition = ((PipeBlock) state.getBlock()).definition;
        behaviour = definition.behaviour.apply(this);
        flow = switch (definition.flow) {
            case ITEM -> new ItemFlow(this);
            case FLUID -> new FluidFlow(this);
            case POWER -> new PowerFlow(this);
            case STRUCTURE -> new StructureFlow(this);
        };
    }

    public PipeDefinition definition() {
        return definition;
    }

    public PipeBehaviour behaviour() {
        return behaviour;
    }

    public PipeFlow flow() {
        return flow;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (flow instanceof ItemFlow items) items.onLevelSet();
    }

    // Connections.

    public boolean isConnected(Direction face) {
        return connectedType(face) != null;
    }

    @Nullable
    public ConnectedType connectedType(Direction face) {
        if (level != null && level.isClientSide) {
            if (!PipeBlock.isConnected(getBlockState(), face)) return null;
            return neighbourTile(face) instanceof PipeBlockEntity ? ConnectedType.PIPE : ConnectedType.TILE;
        }
        return connections[face.ordinal()];
    }

    @Nullable
    public BlockEntity neighbourTile(Direction face) {
        if (level == null) return null;
        BlockPos pos = worldPosition.relative(face);
        return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
    }

    public void markForUpdate() {
        updateMarked = true;
    }

    private void updateConnections() {
        updateMarked = false;
        ConnectedType[] old = connections.clone();
        for (Direction face : Direction.values()) connections[face.ordinal()] = computeConnection(face);
        for (Direction face : Direction.values()) {
            int i = face.ordinal();
            if ((old[i] == null) != (connections[i] == null) && neighbourTile(face) instanceof PipeBlockEntity other) {
                other.markForUpdate();
            }
        }
        refreshState();
    }

    @Nullable
    private ConnectedType computeConnection(Direction face) {
        if (isBlocked(face)) return null;
        BlockEntity tile = neighbourTile(face);
        if (tile == null || tile.isRemoved()) return null;
        if (tile instanceof PipeBlockEntity other && !other.isBlocked(face.getOpposite())) {
            Direction back = face.getOpposite();
            boolean connect = behaviour.canConnect(face, other.behaviour) && other.behaviour.canConnect(back, behaviour)
                    && flow.canConnect(face, other.flow) && other.flow.canConnect(back, flow);
            return connect ? ConnectedType.PIPE : null;
        }
        return behaviour.canConnectTile(face, tile) && flow.canConnectTile(face, tile) ? ConnectedType.TILE : null;
    }

    // Pluggables.

    @Nullable
    public PipePluggable getPluggable(Direction side) {
        return pluggables[side.ordinal()];
    }

    /** True when a pluggable on {@code side} stops the pipe connecting through it. */
    public boolean isBlocked(Direction side) {
        PipePluggable plug = pluggables[side.ordinal()];
        return plug != null && plug.isBlocking();
    }

    /** Upstream {@code TilePipeHolder.replacePluggable}. */
    @Nullable
    public PipePluggable replacePluggable(Direction side, @Nullable PipePluggable with) {
        PipePluggable old = pluggables[side.ordinal()];
        pluggables[side.ordinal()] = with;
        updatePluggableShape();
        if (level != null && !level.isClientSide) {
            markForUpdate();
            queueSync();
            setChanged();
            Platform.get().invalidatePorts(this);
            level.neighborChanged(worldPosition.relative(side), getBlockState().getBlock(), worldPosition);
        }
        return old;
    }

    /** Upstream {@code BlockPipeHolder.removePluggable}. */
    public void removePluggable(Direction side, List<ItemStack> drops) {
        PipePluggable removed = replacePluggable(side, null);
        if (removed != null) {
            removed.onRemove();
            removed.addDrops(drops);
        }
    }

    /** The union of the pluggables' boxes, which {@link PipeBlock} adds to the pipe's own shape. */
    public VoxelShape pluggableShape() {
        return pluggableShape;
    }

    /** {@code pipeShape} joined with {@link #pluggableShape()}. Shapes are also read off the main thread, so the cache is one volatile record. */
    VoxelShape shapeWith(VoxelShape pipeShape) {
        VoxelShape plugs = pluggableShape;
        CombinedShape cached = combinedShape;
        if (cached == null || cached.pipe != pipeShape || cached.plugs != plugs) {
            cached = new CombinedShape(pipeShape, plugs, Shapes.or(pipeShape, plugs).optimize());
            combinedShape = cached;
        }
        return cached.combined;
    }

    private void updatePluggableShape() {
        VoxelShape shape = Shapes.empty();
        for (PipePluggable plug : pluggables) {
            if (plug != null) shape = Shapes.or(shape, Shapes.create(plug.getBoundingBox()));
        }
        pluggableShape = shape.optimize();
    }

    private record CombinedShape(VoxelShape pipe, VoxelShape plugs, VoxelShape combined) {}

    /** Mirrors connections and the behaviour's alternate faces into the block state for the model. */
    public void refreshState() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof PipeBlock)) return;
        BlockState updated = state;
        for (Direction face : Direction.values()) {
            PipeSide side = connections[face.ordinal()] == null ? PipeSide.NONE
                    : behaviour.isSpecialSide(face) ? PipeSide.SPECIAL : PipeSide.CONNECTED;
            updated = updated.setValue(PipeBlock.SIDES.get(face), side);
        }
        if (updated != state) level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
    }

    // Ticking.

    void serverTick() {
        if (updateMarked) updateConnections();
        behaviour.onTick();
        flow.serverTick();
        tickPluggables();
        flushSync();
    }

    void clientTick() {
        flow.clientTick();
        tickPluggables();
    }

    private void tickPluggables() {
        for (PipePluggable plug : pluggables) {
            if (plug != null) plug.onTick();
        }
    }

    // Ports. A pluggable's own port comes first; a blocking pluggable hides the pipe's.

    @Nullable
    @Override
    public ItemPort itemPort(@Nullable Direction side) {
        if (side == null) return null;
        PipePluggable plug = getPluggable(side);
        if (plug != null && (plug.itemPort() != null || plug.isBlocking())) return plug.itemPort();
        return flow.itemPort(side);
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        if (side == null) return null;
        PipePluggable plug = getPluggable(side);
        if (plug != null && (plug.fluidPort() != null || plug.isBlocking())) return plug.fluidPort();
        return flow.fluidPort(side);
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        if (side == null) return null;
        PipePluggable plug = getPluggable(side);
        if (plug != null && (plug.energyPort() != null || plug.isBlocking())) return plug.energyPort();
        EnergyPort port = flow.energyPort(side);
        return port != null ? port : behaviour.energyPort(side);
    }

    @Override
    public boolean canInjectItems(Direction from) {
        return flow instanceof ItemFlow items && items.canInjectItems(from);
    }

    @Override
    public ItemStack injectItem(ItemStack stack, boolean doAdd, Direction from, double speed) {
        return flow instanceof ItemFlow items ? items.injectItem(stack, doAdd, from, speed) : stack;
    }

    // Persistence and client sync.

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag behaviourTag = new CompoundTag();
        behaviour.save(behaviourTag);
        tag.put("behaviour", behaviourTag);
        CompoundTag flowTag = new CompoundTag();
        flow.save(flowTag);
        tag.put("flow", flowTag);
        tag.put("plugs", writePluggables(false));
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        behaviour.load(tag.getCompound("behaviour"));
        flow.load(tag.getCompound("flow"));
        readPluggables(tag.getCompound("plugs"), false);
        updateMarked = true;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        flow.writeClient(tag);
        tag.put("plugs", writePluggables(true));
    }

    @Override
    protected void readClient(CompoundTag tag) {
        flow.readClient(tag);
        readPluggables(tag.getCompound("plugs"), true);
    }

    /** Upstream {@code PluggableHolder.writeToNbt}: each face's pluggable id and data, keyed by face name. */
    private CompoundTag writePluggables(boolean client) {
        CompoundTag plugs = new CompoundTag();
        for (Direction face : Direction.values()) {
            PipePluggable plug = getPluggable(face);
            if (plug == null) continue;
            CompoundTag data = new CompoundTag();
            if (client) plug.writeClient(data);
            else plug.save(data);
            CompoundTag entry = new CompoundTag();
            entry.putString("id", plug.definition.identifier.toString());
            entry.put("data", data);
            plugs.put(face.getSerializedName(), entry);
        }
        return plugs;
    }

    private void readPluggables(CompoundTag plugs, boolean client) {
        for (Direction face : Direction.values()) {
            CompoundTag entry = plugs.getCompound(face.getSerializedName());
            ResourceLocation id = entry.isEmpty() ? null : ResourceLocation.tryParse(entry.getString("id"));
            PluggableDefinition definition = id == null ? null : PluggableDefinition.get(id);
            if (definition == null) {
                if (!entry.isEmpty()) LOGGER.warn("Unknown pluggable '{}' on the {} of the pipe at {}", entry.getString("id"), face, worldPosition);
                pluggables[face.ordinal()] = null;
                continue;
            }
            PipePluggable plug = pluggables[face.ordinal()];
            if (!client || plug == null || plug.definition != definition) plug = definition.create(this, face);
            if (client) plug.readClient(entry.getCompound("data"));
            else plug.load(entry.getCompound("data"));
            pluggables[face.ordinal()] = plug;
        }
        updatePluggableShape();
    }
}
