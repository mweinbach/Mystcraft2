/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import ports.extremereactors.corelib.capability.ICapabilityProvider;
import ports.extremereactors.forge.ForgeTransfer;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Forge side of the block entity base: publishes the corelib model data through Forge's model data system and
 * the corelib handlers as Forge capabilities.
 * The Fabric source set has a class with the same name and API.
 */
public abstract class PlatformBlockEntity extends BlockEntity {

    /** The single Forge model property carrying the corelib model data. */
    public static final ModelProperty<ports.extremereactors.corelib.client.model.data.ModelData> MODEL_DATA = new ModelProperty<>();

    protected PlatformBlockEntity(final BlockEntityType<?> type, final BlockPos position, final BlockState state) {

        super(type, position, state);
        this._exposed = ForgeTransfer.newExposedMap();
    }

    protected abstract ports.extremereactors.corelib.client.model.data.ModelData getCurrentModelData();

    /**
     * The model data returned by {@link #getCurrentModelData()} changed.
     */
    protected void onModelDataInvalidated() {
        this.requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {

        final ports.extremereactors.corelib.client.model.data.ModelData data = this.getCurrentModelData();

        return data.isEmpty() ? ModelData.EMPTY : ModelData.builder().with(MODEL_DATA, data).build();
    }

    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> capability, @Nullable final Direction side) {

        if (!this.remove && this instanceof ICapabilityProvider provider) {

            final LazyOptional<T> exposed = ForgeTransfer.expose(provider, this._exposed, capability, side);

            if (exposed.isPresent()) {
                return exposed;
            }
        }

        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {

        super.invalidateCaps();
        ForgeTransfer.invalidateAll(this._exposed);
    }

    private final Map<ports.extremereactors.corelib.capability.LazyOptional<?>, LazyOptional<?>> _exposed;
}
