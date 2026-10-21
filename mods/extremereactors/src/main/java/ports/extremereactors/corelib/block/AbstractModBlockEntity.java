/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.CodeHelper;
import ports.extremereactors.corelib.IDebugMessages;
import ports.extremereactors.corelib.IDebuggable;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.ICapabilityProvider;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.event.Event;
import ports.extremereactors.corelib.event.IEvent;
import ports.extremereactors.corelib.internal.Lib;
import ports.extremereactors.corelib.network.messages.TileCommandMessage;
import ports.extremereactors.platform.Platform;
import ports.extremereactors.platform.PlatformBlockEntity;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base block entity. All persistent and client-synchronized state goes through {@link ISyncableEntity}: disk saves
 * and the initial chunk data use {@link SyncReason#FullSync}, block entity data packets and the periodic GUI updates
 * {@link SyncReason#NetworkUpdate}.
 */
public abstract class AbstractModBlockEntity
        extends PlatformBlockEntity
        implements ICapabilityProvider, ISyncableEntity, IDebuggable {

    public AbstractModBlockEntity(final BlockEntityType<?> type, final BlockPos position, final BlockState blockState) {

        super(type, position, blockState);
        this._commandHandler = ITileCommandHandler.NONE;
        this._modelData = null;
    }

    @Nullable
    public Level getWorld() {
        return this.getLevel();
    }

    public Block getBlockType() {
        return this.getBlockState().getBlock();
    }

    //region logical sides

    public boolean calledByLogicalServer() {
        return null != this.level && !this.level.isClientSide();
    }

    public boolean calledByLogicalClient() {
        return null != this.level && this.level.isClientSide();
    }

    public void callOnLogicalServer(final Runnable code) {

        if (this.calledByLogicalServer()) {
            code.run();
        }
    }

    public void callOnLogicalServer(final Consumer<Level> code) {

        if (this.calledByLogicalServer()) {
            code.accept(this.level);
        }
    }

    public void callOnLogicalClient(final Runnable code) {

        if (this.calledByLogicalClient()) {
            code.run();
        }
    }

    public void callOnLogicalClient(final Consumer<Level> code) {

        if (this.calledByLogicalClient()) {
            code.accept(this.level);
        }
    }

    public void callOnLogicalSide(final Runnable serverCode, final Runnable clientCode) {

        if (null != this.level) {
            CodeHelper.callOnLogicalSide(this.level, serverCode, clientCode);
        }
    }

    public <T> T callOnLogicalServer(final Supplier<T> code, final Supplier<T> invalidSideReturnValue) {
        return this.calledByLogicalServer() ? code.get() : invalidSideReturnValue.get();
    }

    public <T> T callOnLogicalClient(final Supplier<T> code, final Supplier<T> invalidSideReturnValue) {
        return this.calledByLogicalClient() ? code.get() : invalidSideReturnValue.get();
    }

    //endregion
    //region data updates

    /**
     * Raised by {@link #onDataUpdate()}: the data this block entity shows in its screen changed.
     */
    public final IEvent<Runnable> DataUpdated = new Event<>();

    /**
     * The data shown by this block entity changed, for example because its multiblock controller updated.
     */
    protected void onDataUpdate() {

        this.DataUpdated.raise(Runnable::run);

        // A controller's face, for example, follows whether its machine is running.
        if (this.calledByLogicalClient() && null != this._modelData &&
                !this._modelData.equals(this.getUpdatedModelData())) {
            this.markForRenderUpdate();
        }
    }

    /** Raise {@link #DataUpdated}, as when new data for this block entity arrives on the client. */
    public void raiseDataUpdated() {
        this.onDataUpdate();
    }

    //endregion
    //region capabilities

    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> capability, @Nullable final Direction side) {
        return LazyOptional.empty();
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {
        return data;
    }

    //endregion
    //region persistence and client synchronization

    /**
     * The key of the compound holding the {@link SyncReason#FullSync} data in the saved block entity NBT. Extreme
     * Reactors' loot tables copy their stack-storable values from it.
     */
    public static final String SYNC_PAYLOAD_KEY = "zcvase_payload";

    /**
     * The key of the compound holding the {@link SyncReason#NetworkUpdate} data in a block entity data packet.
     * Multiblock parts only cache full-sync controller data until their next validation, so updates for an
     * already assembled client machine (the turbine rotor speed, the reactor fuel rods) must use this one.
     */
    public static final String NETWORK_PAYLOAD_KEY = "zcvase_update";

    @Override
    public void load(final CompoundTag data) {

        super.load(data);

        if (data.contains(SYNC_PAYLOAD_KEY, Tag.TAG_COMPOUND)) {
            this.syncDataFrom(data.getCompound(SYNC_PAYLOAD_KEY), SyncReason.FullSync);
        }

        if (data.contains(NETWORK_PAYLOAD_KEY, Tag.TAG_COMPOUND)) {
            this.syncDataFrom(data.getCompound(NETWORK_PAYLOAD_KEY), SyncReason.NetworkUpdate);
        }

        if (this.calledByLogicalClient()) {

            this.markForRenderUpdate();
            this.raiseDataUpdated();
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag data) {

        super.saveAdditional(data);
        data.put(SYNC_PAYLOAD_KEY, this.syncDataTo(new CompoundTag(), SyncReason.FullSync));
    }

    @Override
    public CompoundTag getUpdateTag() {

        final CompoundTag data = new CompoundTag();

        data.put(SYNC_PAYLOAD_KEY, this.syncDataTo(new CompoundTag(), SyncReason.FullSync));
        return data;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        final CompoundTag data = new CompoundTag();

        data.put(NETWORK_PAYLOAD_KEY, this.syncDataTo(new CompoundTag(), SyncReason.NetworkUpdate));
        return ClientboundBlockEntityDataPacket.create(this, blockEntity -> data);
    }

    //endregion
    //region notifications

    /**
     * Server: send this block entity to the clients tracking it. Client: re-render the block.
     */
    public void notifyBlockUpdate() {

        if (null != this.level && !this.isRemoved()) {

            final BlockState state = this.getBlockState();

            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public static void notifyBlockUpdate(final Collection<? extends AbstractModBlockEntity> entities) {
        entities.forEach(AbstractModBlockEntity::notifyBlockUpdate);
    }

    /**
     * Save this block entity and send it to the clients.
     */
    public void notifyTileEntityUpdate() {

        this.setChanged();
        this.notifyBlockUpdate();
    }

    /**
     * Mark the chunk as needing a save, without the comparator update {@link #setChanged()} does.
     */
    public void markChunkDirty() {

        if (null != this.level && this.level.hasChunkAt(this.worldPosition)) {
            this.level.blockEntityChanged(this.worldPosition);
        }
    }

    /**
     * Client: recompute the model data and re-render the block. Does nothing on the server.
     */
    public void markForRenderUpdate() {

        if (this.calledByLogicalClient()) {

            final BlockState state = this.getBlockState();

            this.invalidateModelData();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    /**
     * Client: forget the cached model data so that the next render of the block recomputes it. Unlike
     * {@link #markForRenderUpdate()} this does not re-render the block.
     */
    public void invalidateModelData() {

        this._modelData = null;
        this.onModelDataInvalidated();
    }

    /**
     * Make the clients re-render this block: the server sends the block entity to them, which re-renders it when
     * it arrives.
     */
    public void requestClientRenderUpdate() {
        this.callOnLogicalSide(this::notifyBlockUpdate, this::markForRenderUpdate);
    }

    /**
     * Tell the adjacent {@link INeighborChangeListener} block entities that this one changed.
     */
    protected void notifyNeighborsOfTileChange() {

        if (null != this.level) {
            ModBlock.notifyNeighborListeners(this.level, this.worldPosition);
        }
    }

    //endregion
    //region model data

    /**
     * @return the data for the baked model of this block. Called on the client only.
     */
    protected ModelData getUpdatedModelData() {
        return ModelData.EMPTY;
    }

    @Override
    protected final ModelData getCurrentModelData() {

        ModelData data = this._modelData;

        if (null == data) {
            this._modelData = data = this.getUpdatedModelData();
        }

        return data;
    }

    //endregion
    //region commands

    protected void setCommandDispatcher(final ITileCommandHandler handler) {
        this._commandHandler = handler;
    }

    public void handleCommand(final LogicalSide sourceSide, final String name, final CompoundTag parameters) {
        this._commandHandler.handleCommand(sourceSide, name, parameters);
    }

    /**
     * Client only: ask the server-side copy of this block entity to run a command.
     */
    public void sendCommandToServer(final String name, final CompoundTag parameters) {

        if (this.calledByLogicalClient()) {
            Lib.network().sendToServer(new TileCommandMessage(this.worldPosition, name, parameters));
        }
    }

    public void sendCommandToServer(final String name) {
        this.sendCommandToServer(name, new CompoundTag());
    }

    //endregion
    //region screens

    /**
     * Client side: the block entity a screen was opened for. {@link ModBlock} writes its position as the extra data
     * of the open-screen packet.
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractModBlockEntity> T getGuiClientBlockEntity(final FriendlyByteBuf networkData) {

        final BlockPos position = networkData.readBlockPos();

        return (T) Platform.network().clientLevel()
                .map(level -> level.getBlockEntity(position))
                .filter(AbstractModBlockEntity.class::isInstance)
                .orElseThrow(() -> new IllegalStateException("No block entity for the screen at " + position.toShortString()));
    }

    //endregion
    //region block events

    /**
     * The block of this block entity is being replaced by a different block. Drop inventories and the like here.
     */
    public void onBlockReplaced(final BlockState state, final Level world, final BlockPos position,
                                final BlockState newState, final boolean isMoving) {
    }

    /**
     * @return true if right-clicking the block should open the screen of this block entity, which must then be a
     * {@link net.minecraft.world.MenuProvider}
     */
    public boolean canOpenGui(final Level world, final BlockPos position, final BlockState state) {
        return false;
    }

    /**
     * The chunk holding this block entity is being unloaded. Called by Forge and by the corelib Fabric adapter.
     */
    public void onChunkUnloaded() {
    }

    //endregion
    //region IDebuggable

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {
        messages.addUnlocalized("Block entity: %1$s at %2$s", this.getClass().getSimpleName(), this.worldPosition.toShortString());
    }

    //endregion
    //region internals

    private ITileCommandHandler _commandHandler;
    @Nullable
    private ModelData _modelData;

    //endregion
}
