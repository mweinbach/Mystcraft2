package ports.buildcraft.transport;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.Injectable;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.PortHolder;
import ports.buildcraft.transport.behaviour.PipeBehaviour;
import ports.buildcraft.transport.flow.FluidFlow;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.flow.PipeFlow;
import ports.buildcraft.transport.flow.PowerFlow;

/**
 * Upstream {@code TilePipeHolder} and {@code Pipe}: owns one behaviour and one flow, and works out
 * which faces connect to neighbouring pipes and blocks.
 */
public class PipeBlockEntity extends SyncedBlockEntity implements PortHolder, Injectable {
    public enum ConnectedType { PIPE, TILE }

    private final PipeDefinition definition;
    private final PipeBehaviour behaviour;
    private final PipeFlow flow;
    private final ConnectedType[] connections = new ConnectedType[6];
    private boolean updateMarked = true;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(BCTransport.PIPE_TYPE.get(), pos, state);
        definition = ((PipeBlock) state.getBlock()).definition;
        behaviour = definition.behaviour.apply(this);
        flow = switch (definition.flow) {
            case ITEM -> new ItemFlow(this);
            case FLUID -> new FluidFlow(this);
            case POWER -> new PowerFlow(this);
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
        BlockEntity tile = neighbourTile(face);
        if (tile == null || tile.isRemoved()) return null;
        if (tile instanceof PipeBlockEntity other) {
            Direction back = face.getOpposite();
            boolean connect = behaviour.canConnect(face, other.behaviour) && other.behaviour.canConnect(back, behaviour)
                    && flow.canConnect(face, other.flow) && other.flow.canConnect(back, flow);
            return connect ? ConnectedType.PIPE : null;
        }
        return behaviour.canConnectTile(face, tile) && flow.canConnectTile(face, tile) ? ConnectedType.TILE : null;
    }

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
        flushSync();
    }

    void clientTick() {
        flow.clientTick();
    }

    // Ports.

    @Nullable
    @Override
    public ItemPort itemPort(@Nullable Direction side) {
        return side == null ? null : flow.itemPort(side);
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        return side == null ? null : flow.fluidPort(side);
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        if (side == null) return null;
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
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        behaviour.load(tag.getCompound("behaviour"));
        flow.load(tag.getCompound("flow"));
        updateMarked = true;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        flow.writeClient(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        flow.readClient(tag);
    }
}
