/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model;

import ports.extremereactors.platform.PlatformBakedModel;

/**
 * A baked model whose quads depend on the model data of the block being drawn.
 */
public abstract class AbstractDynamicBakedModel
        extends PlatformBakedModel {

    protected AbstractDynamicBakedModel(final boolean ambientOcclusion, final boolean guiLight3d) {

        this._ambientOcclusion = ambientOcclusion;
        this._guiLight3d = guiLight3d;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this._ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return this._guiLight3d;
    }

    @Override
    public boolean usesBlockLight() {
        return this._guiLight3d;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    private final boolean _ambientOcclusion;
    private final boolean _guiLight3d;
}
