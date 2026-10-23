/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client.model;

import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import ports.extremereactors.corelib.client.model.ICustomModelBuilder;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Runs the {@link ICustomModelBuilder}s the way Forge does, one after the other against one map of baked models,
 * but one model at a time, as Fabric's model loading API asks for each model.
 * <p>
 * Forge hands every builder the same mutable map of baked models, so a builder sees the original model of an id
 * that it or a later builder replaces, and the replacement of an id that an earlier builder replaced. A
 * {@link Session} gives each builder a map with exactly those answers, computed on demand: the original models come
 * from {@code original}, the models no builder replaces from {@code baked}. The replacement of an id is what the last
 * builder that declares it puts in its map.
 */
public final class ModelReplacements {

    public ModelReplacements(final List<? extends ICustomModelBuilder> builders) {

        this._builders = List.copyOf(builders);
        this._declaring = new HashMap<>();
        this._blocks = new HashSet<>();

        for (int index = 0; index < this._builders.size(); ++index) {
            for (final ResourceLocation id : this._builders.get(index).getReplacedModels()) {

                final List<Integer> declaring = this._declaring.computeIfAbsent(id, key -> new ArrayList<>(1));

                if (declaring.isEmpty() || declaring.get(declaring.size() - 1) != index) {
                    declaring.add(index);
                }

                if (id instanceof ModelResourceLocation) {
                    this._blocks.add(new ResourceLocation(id.getNamespace(), id.getPath()));
                } else {
                    LOGGER.error("Model {} is not a block state model; Fabric cannot replace it", id);
                }
            }
        }
    }

    /**
     * @return true if a builder replaces this model
     */
    public boolean replaces(final ResourceLocation id) {
        return this._declaring.containsKey(id);
    }

    /**
     * @return the ids of the blocks that have a replaced block state model. Fabric must load every state model of
     * these blocks itself: if vanilla loaded one of them, it would load the replaced ones too.
     */
    public Set<ResourceLocation> blocks() {
        return Collections.unmodifiableSet(this._blocks);
    }

    /**
     * Start one model reload.
     *
     * @param original the model of a replaced id before any builder replaced it
     * @param baked    the model of an id no builder replaces, including {@code ModelBakery.MISSING_MODEL_LOCATION}
     */
    public Session newSession(final Function<ResourceLocation, BakedModel> original,
                              final Function<ResourceLocation, BakedModel> baked) {
        return new Session(original, baked);
    }

    /**
     * The builders' work for one model reload. Each builder runs once, the first time one of its models, or a model
     * that a later builder sees through it, is asked for.
     */
    public final class Session {

        private Session(final Function<ResourceLocation, BakedModel> original,
                        final Function<ResourceLocation, BakedModel> baked) {

            this._original = original;
            this._baked = baked;
            this._results = new ArrayList<>(Collections.nCopies(ModelReplacements.this._builders.size(), null));
            this._running = new boolean[ModelReplacements.this._builders.size()];
        }

        /**
         * @return the final model of a replaced id
         */
        public BakedModel get(final ResourceLocation id) {

            final List<Integer> declaring = ModelReplacements.this._declaring.get(id);

            if (null == declaring) {
                throw new IllegalArgumentException("No model builder replaces " + id);
            }

            return this.resultOf(declaring.get(declaring.size() - 1), id);
        }

        /** The model builder {@code index} left for {@code id}, which it declared. */
        private BakedModel resultOf(final int index, final ResourceLocation id) {

            final BakedModel replacement = this.run(index).get(id);

            return null != replacement ? replacement : this.seenBy(index, id);
        }

        /** The model builder {@code index} finds for {@code id} before it changes anything. */
        private BakedModel seenBy(final int index, final ResourceLocation id) {

            final List<Integer> declaring = ModelReplacements.this._declaring.get(id);

            if (null == declaring) {
                return this._baked.apply(id);
            }

            for (int i = declaring.size() - 1; i >= 0; --i) {
                if (declaring.get(i) < index) {
                    return this.resultOf(declaring.get(i), id);
                }
            }

            return this._original.apply(id);
        }

        private Map<ResourceLocation, BakedModel> run(final int index) {

            Map<ResourceLocation, BakedModel> result = this._results.get(index);

            if (null != result) {
                return result;
            }

            if (this._running[index]) {
                // a builder only looks through earlier builders, so this cannot happen
                throw new IllegalStateException("Model builder " + index + " depends on itself");
            }

            this._running[index] = true;

            final ICustomModelBuilder builder = ModelReplacements.this._builders.get(index);
            final BuilderView view = new BuilderView(index);

            try {
                builder.onBakeModels(view);
            } finally {
                this._running[index] = false;
            }

            result = new HashMap<>();

            for (final ResourceLocation id : builder.getReplacedModels()) {

                final BakedModel model = view._replaced.get(id);

                if (null != model) {
                    result.put(id, model);
                }
            }

            for (final ResourceLocation id : view._replaced.keySet()) {
                if (!result.containsKey(id)) {
                    LOGGER.warn("{} replaced model {} without declaring it; Fabric ignores the replacement",
                            builder.getClass().getName(), id);
                }
            }

            this._results.set(index, result);
            return result;
        }

        /**
         * The baked models as one builder sees them. Builders only look models up and put their replacements.
         */
        private final class BuilderView
                extends AbstractMap<ResourceLocation, BakedModel> {

            BuilderView(final int index) {

                this._index = index;
                this._replaced = new LinkedHashMap<>();
            }

            @Nullable
            @Override
            public BakedModel get(final Object key) {

                if (!(key instanceof ResourceLocation id)) {
                    return null;
                }

                final BakedModel replaced = this._replaced.get(id);

                return null != replaced ? replaced : Session.this.seenBy(this._index, id);
            }

            @Override
            public BakedModel getOrDefault(final Object key, final BakedModel defaultValue) {

                final BakedModel model = this.get(key);

                return null != model ? model : defaultValue;
            }

            @Override
            public boolean containsKey(final Object key) {
                return null != this.get(key);
            }

            @Override
            public BakedModel put(final ResourceLocation key, final BakedModel value) {
                return this._replaced.put(key, value);
            }

            @Override
            public Set<Entry<ResourceLocation, BakedModel>> entrySet() {
                throw new UnsupportedOperationException("Model builders can only look baked models up on Fabric");
            }

            private final int _index;
            private final Map<ResourceLocation, BakedModel> _replaced;
        }

        private final Function<ResourceLocation, BakedModel> _original;
        private final Function<ResourceLocation, BakedModel> _baked;
        private final List<Map<ResourceLocation, BakedModel>> _results;
        private final boolean[] _running;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<ICustomModelBuilder> _builders;
    private final Map<ResourceLocation, List<Integer>> _declaring;
    private final Set<ResourceLocation> _blocks;
}
