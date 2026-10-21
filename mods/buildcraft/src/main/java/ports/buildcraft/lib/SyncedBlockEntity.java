package ports.buildcraft.lib;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entities that mirror render state to clients through vanilla block entity data packets, so
 * no loader networking is needed. {@link #writeClient} and {@link #readClient} carry that state.
 */
public abstract class SyncedBlockEntity extends BlockEntity {
    private boolean syncQueued;

    protected SyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void writeClient(CompoundTag tag);

    protected abstract void readClient(CompoundTag tag);

    /** Requests a client update at the end of this tick. */
    public void queueSync() {
        syncQueued = true;
    }

    /** Whether {@link #flushSync()} still has a client update to send. */
    public boolean hasPendingClientUpdate() {
        return syncQueued;
    }

    /** Sends a queued client update; call once at the end of the server tick. */
    protected void flushSync() {
        if (syncQueued && level != null && !level.isClientSide) {
            syncQueued = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeClient(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Vanilla routes the update tag through {@code load}; client-side loads only read render state. */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (level != null && level.isClientSide) {
            readClient(tag);
        } else {
            loadServer(tag);
        }
    }

    protected abstract void loadServer(CompoundTag tag);
}
