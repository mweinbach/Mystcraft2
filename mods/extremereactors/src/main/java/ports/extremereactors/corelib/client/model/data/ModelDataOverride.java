/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.data;

import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Lets a renderer choose the model data of the blocks it draws, for previews of blocks that are not in a world
 * (a handbook's multiblock pictures, for example). The choice only applies to the calling thread, so chunk meshes
 * built meanwhile on other threads keep their block entities' data.
 */
public final class ModelDataOverride {

    /**
     * Run the action with the mapper deciding the model data of every block state rendered meanwhile on this thread.
     * The mapper may return null to keep a state's own data.
     */
    public static void with(final Function<BlockState, ModelData> mapper, final Runnable action) {

        final Function<BlockState, ModelData> previous = s_mapper.get();

        s_mapper.set(mapper);

        try {
            action.run();
        } finally {
            s_mapper.set(previous);
        }
    }

    /**
     * @return the model data chosen for the state, or null when no override is active or it keeps the state's own
     */
    @Nullable
    public static ModelData get(@Nullable final BlockState state) {

        final Function<BlockState, ModelData> mapper = s_mapper.get();

        return null != mapper && null != state ? mapper.apply(state) : null;
    }

    //region internals

    private ModelDataOverride() {
    }

    private static final ThreadLocal<Function<BlockState, ModelData>> s_mapper = new ThreadLocal<>();

    //endregion
}
