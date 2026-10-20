/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ports.extremereactors.corelib.client.model.data.ModelData;

/**
 * Fabric side of the block entity base: publishes the corelib model data as the Fabric render attachment.
 * The Forge source set has a class with the same name and API.
 */
public abstract class PlatformBlockEntity extends BlockEntity implements RenderAttachmentBlockEntity {

    public static final AABB INFINITE_EXTENT_AABB = new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    protected PlatformBlockEntity(final BlockEntityType<?> type, final BlockPos position, final BlockState state) {
        super(type, position, state);
    }

    /**
     * Mirrors Forge's render bounding box. Fabric does not cull block entity renderers by it: renderers that draw
     * outside their block override {@code BlockEntityRenderer#shouldRenderOffScreen} instead.
     */
    public AABB getRenderBoundingBox() {
        return new AABB(this.getBlockPos());
    }

    protected abstract ModelData getCurrentModelData();

    /**
     * The model data returned by {@link #getCurrentModelData()} changed.
     */
    protected void onModelDataInvalidated() {
    }

    @Override
    public Object getRenderAttachmentData() {
        return this.getCurrentModelData();
    }
}
