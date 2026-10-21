/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.model.data.ModelData;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ICapabilitySerializable;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.platform.Platform;

/**
 * Methods Forge adds to {@link BlockEntity}, including those of its capability provider base class, injected into it
 * on Fabric. Mixins call {@link #onLoad}, {@link #onChunkUnloaded}, {@link #invalidateCaps} and {@link #reviveCaps} at
 * the points Forge does, and the client networking mixin hands update packets and tags to {@link #onDataPacket} and
 * {@link #handleUpdateTag}. The model data reaches Fabric's renderer as the block entity's render attachment.
 * Capabilities nothing attached fall back to the loader's API, so other mods' storages answer Forge's item, fluid and
 * energy capabilities.
 */
public interface IForgeBlockEntity extends ICapabilitySerializable<CompoundTag> {

    AABB INFINITE_EXTENT_AABB = new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
          Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    private BlockEntity self() {
        return (BlockEntity) this;
    }

    @NotNull
    @Override
    default <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> attached = ((AttachedCapabilities.Holder) this).mekanism$capabilities().getCapability(cap, side);
        return attached.isPresent() ? attached : Platform.foreignCapability(this, cap, side);
    }

    default void invalidateCaps() {
        ((AttachedCapabilities.Holder) this).mekanism$capabilities().invalidate();
    }

    default void reviveCaps() {
        ((AttachedCapabilities.Holder) this).mekanism$capabilities().revive();
    }

    @Override
    default void deserializeNBT(CompoundTag nbt) {
        self().load(nbt);
    }

    @Override
    default CompoundTag serializeNBT() {
        return self().saveWithFullMetadata();
    }

    default void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            self().load(tag);
        }
    }

    default void handleUpdateTag(CompoundTag tag) {
        self().load(tag);
    }

    default void onChunkUnloaded() {
        invalidateCaps();
    }

    default void onLoad() {
        requestModelDataUpdate();
    }

    /**
     * The box the level renderer tests against the frustum. Matches Forge: the block's collision shape, except the
     * vanilla blocks whose renderer draws outside that shape.
     */
    default AABB getRenderBoundingBox() {
        BlockEntity blockEntity = self();
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        Block block = state.getBlock();
        if (block == Blocks.ENCHANTING_TABLE) {
            return new AABB(pos, pos.offset(1, 1, 1));
        } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            return new AABB(pos.offset(-1, 0, -1), pos.offset(2, 2, 2));
        } else if (block == Blocks.STRUCTURE_BLOCK || block == Blocks.BEACON) {
            return INFINITE_EXTENT_AABB;
        }
        try {
            VoxelShape collisionShape = state.getCollisionShape(blockEntity.getLevel(), pos);
            if (!collisionShape.isEmpty()) {
                return collisionShape.bounds().move(pos);
            }
        } catch (Exception ignored) {
            // A block entity can arrive before its chunk. Forge uses a one-block box rather than failing the render.
            return new AABB(pos.offset(-1, 0, -1), pos.offset(1, 1, 1));
        }
        return INFINITE_EXTENT_AABB;
    }

    /**
     * Fabric's renderer reads {@link #getModelData} when it rebuilds the chunk, so there is no cached copy to refresh.
     */
    default void requestModelDataUpdate() {
    }

    @NotNull
    default ModelData getModelData() {
        return ModelData.EMPTY;
    }
}
