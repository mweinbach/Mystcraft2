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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.model.data.ModelData;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ICapabilitySerializable;
import ports.mekanism.lib.common.util.LazyOptional;

/**
 * Methods Forge adds to {@link BlockEntity}, including those of its capability provider base class, injected into it
 * on Fabric. Mixins call {@link #onLoad}, {@link #onChunkUnloaded}, {@link #invalidateCaps} and {@link #reviveCaps} at
 * the points Forge does, and the client networking mixin hands update packets and tags to {@link #onDataPacket} and
 * {@link #handleUpdateTag}. The model data reaches Fabric's renderer as the block entity's render attachment.
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
        return ((AttachedCapabilities.Holder) this).mekanism$capabilities().getCapability(cap, side);
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

    default AABB getRenderBoundingBox() {
        BlockPos pos = self().getBlockPos();
        return new AABB(pos.offset(-1, 0, -1), pos.offset(2, 2, 2));
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
