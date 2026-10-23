package ports.buildcraft.transport;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
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
import ports.buildcraft.transport.wire.PipeWires;
import ports.buildcraft.transport.wire.WireBetween;
import ports.buildcraft.transport.wire.WirePart;
import ports.buildcraft.transport.wire.WireSystems;

/**
 * Upstream {@code TilePipeHolder} and {@code Pipe}: owns one behaviour, one flow, a pluggable on each
 * face and the pipe's wires, and works out which faces connect to neighbouring pipes and blocks.
 */
public class PipeBlockEntity extends SyncedBlockEntity implements PortHolder, Injectable {
    public enum ConnectedType { PIPE, TILE }

    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    private final PipeDefinition definition;
    private final PipeBehaviour behaviour;
    private final PipeFlow flow;
    private final ConnectedType[] connections = new ConnectedType[6];
    private final PipePluggable[] pluggables = new PipePluggable[6];
    private final PipeWires wires = new PipeWires(this);
    private volatile VoxelShape extraShape = Shapes.empty();
    @Nullable
    private volatile CombinedShape combinedShape;
    private boolean updateMarked = true;
    private int[] redstoneOutput = new int[6];
    private int[] oldRedstoneOutput = {-1, -1, -1, -1, -1, -1};

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

    public PipeWires wires() {
        return wires;
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
        boolean changed = false;
        for (Direction face : Direction.values()) {
            int i = face.ordinal();
            if ((old[i] == null) == (connections[i] == null)) continue;
            changed = true;
            if (neighbourTile(face) instanceof PipeBlockEntity other) other.markForUpdate();
        }
        refreshState();
        // Wires cross only connected faces, so systems joined or split by a blocker must be rebuilt once connections settle.
        if (changed && !wires.parts().isEmpty() && level instanceof ServerLevel server) {
            WireSystems.get(server).rebuildWireSystemsAround(this);
            wires.updateBetweens(false);
        }
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
        redstoneOutput = new int[6];
        PipePluggable old = pluggables[side.ordinal()];
        pluggables[side.ordinal()] = with;
        updateExtraShape();
        if (level != null && !level.isClientSide) {
            if (old != with) WireSystems.get((ServerLevel) level).rebuildWireSystemsAround(this);
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

    /** The union of the pluggables' and wires' boxes, which {@link PipeBlock} adds to the pipe's own shape. */
    public VoxelShape extraShape() {
        return extraShape;
    }

    /** {@code pipeShape} joined with {@link #extraShape()}. Shapes are also read off the main thread, so the cache is one volatile record. */
    VoxelShape shapeWith(VoxelShape pipeShape) {
        VoxelShape plugs = extraShape;
        CombinedShape cached = combinedShape;
        if (cached == null || cached.pipe != pipeShape || cached.plugs != plugs) {
            cached = new CombinedShape(pipeShape, plugs, Shapes.or(pipeShape, plugs).optimize());
            combinedShape = cached;
        }
        return cached.combined;
    }

    private void updateExtraShape() {
        VoxelShape shape = Shapes.empty();
        for (PipePluggable plug : pluggables) {
            if (plug != null) shape = Shapes.or(shape, Shapes.create(plug.getBoundingBox()));
        }
        for (WirePart part : wires.parts().keySet()) shape = Shapes.or(shape, Shapes.create(part.boundingBox));
        for (WireBetween between : wires.betweens().keySet()) shape = Shapes.or(shape, Shapes.create(between.boundingBox));
        extraShape = shape.optimize();
    }

    /** Called by {@link PipeWires} when a wire is added or removed. */
    public void onWiresChanged() {
        updateExtraShape();
        if (level != null && !level.isClientSide) {
            queueSync();
            setChanged();
        }
    }

    /** Called by {@link PipeWires} when the lengths drawn between wires change. */
    public void onWireShapeChanged() {
        updateExtraShape();
    }

    private record CombinedShape(VoxelShape pipe, VoxelShape plugs, VoxelShape combined) {}

    /** Mirrors connections and the behaviour's alternate faces into the block state for the model. */
    public void refreshState() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof PipeBlock)) return;
        BlockState updated = state;
        boolean redstone = false;
        for (Direction face : Direction.values()) {
            PipeSide side = connections[face.ordinal()] == null ? PipeSide.NONE
                    : behaviour.isSpecialSide(face) ? PipeSide.SPECIAL : PipeSide.CONNECTED;
            updated = updated.setValue(PipeBlock.SIDES.get(face), side);
            PipePluggable plug = pluggables[face.ordinal()];
            redstone |= plug != null && plug.canConnectToRedstone(null);
        }
        updated = behaviour.modelState(updated.setValue(PipeBlock.REDSTONE, redstone));
        if (updated != state) level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
    }

    // Redstone.

    /** Upstream {@code TilePipeHolder.getRedstoneInput}: the signal into one face, or the strongest into the pipe. */
    public int getRedstoneInput(@Nullable Direction side) {
        if (level == null) return 0;
        return side == null ? level.getBestNeighborSignal(worldPosition) : level.getSignal(worldPosition.relative(side), side);
    }

    /** Sets what the pipe emits from {@code side}, or from every face when null, until the next tick clears it. */
    public boolean setRedstoneOutput(@Nullable Direction side, int value) {
        if (side == null) Arrays.fill(redstoneOutput, value);
        else redstoneOutput[side.ordinal()] = value;
        return true;
    }

    public int getRedstoneOutput(Direction side) {
        return redstoneOutput[side.ordinal()];
    }

    /** Upstream {@code TilePipeHolder.update}: neighbours hear of a changed output, and so do those of each changed face. */
    private void updateRedstoneNeighbours() {
        if (Arrays.equals(redstoneOutput, oldRedstoneOutput) || level == null) return;
        Block block = getBlockState().getBlock();
        level.updateNeighborsAt(worldPosition, block);
        for (Direction face : Direction.values()) {
            if (oldRedstoneOutput[face.ordinal()] != redstoneOutput[face.ordinal()]) level.updateNeighborsAt(worldPosition.relative(face), block);
        }
        oldRedstoneOutput = redstoneOutput;
        setChanged();
    }

    // Ticking.

    /** Outputs are set afresh each tick by the gates that emit them. */
    void serverTick() {
        redstoneOutput = new int[6];
        if (updateMarked) updateConnections();
        behaviour.onTick();
        flow.serverTick();
        tickPluggables();
        if (wires.tick()) queueSync();
        updateRedstoneNeighbours();
        flushSync();
    }

    void clientTick() {
        flow.clientTick();
        tickPluggables();
        wires.tick();
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
    public ItemStack injectItem(ItemStack stack, boolean doAdd, Direction from, @Nullable DyeColor colour, double speed) {
        return flow instanceof ItemFlow items ? items.injectItem(stack, doAdd, from, colour, speed) : stack;
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
        tag.putIntArray("redstone", redstoneOutput);
        tag.put("wireManager", wires.save());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        behaviour.load(tag.getCompound("behaviour"));
        flow.load(tag.getCompound("flow"));
        readPluggables(tag.getCompound("plugs"), false);
        int[] redstone = tag.getIntArray("redstone");
        if (redstone.length == 6) redstoneOutput = redstone;
        wires.load(tag.getCompound("wireManager"));
        updateExtraShape();
        updateMarked = true;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        flow.writeClient(tag);
        tag.put("plugs", writePluggables(true));
        tag.put("wires", wires.writeClient());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        flow.readClient(tag);
        readPluggables(tag.getCompound("plugs"), true);
        wires.readClient(tag.getCompound("wires"));
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
        updateExtraShape();
    }
}
