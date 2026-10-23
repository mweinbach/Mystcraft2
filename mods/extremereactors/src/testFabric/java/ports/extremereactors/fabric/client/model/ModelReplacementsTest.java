/*
 * Extreme Reactors 1.19.2 port - Fabric adapter tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import ports.extremereactors.corelib.client.model.ICustomModelBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Fabric model provider must hand out the models Forge's model baking completed event leaves: every builder runs
 * once, in order, against one map of baked models.
 */
class ModelReplacementsTest {

    private static final ModelResourceLocation CASING = new ModelResourceLocation("bigreactors", "basic_reactorcasing", "");
    private static final ModelResourceLocation GLASS = new ModelResourceLocation("bigreactors", "basic_reactorglass", "facings=none");
    private static final ModelResourceLocation ROD_X = new ModelResourceLocation("bigreactors", "basic_reactorfuelrod", "axis=x");
    private static final ModelResourceLocation ROD_Y = new ModelResourceLocation("bigreactors", "basic_reactorfuelrod", "axis=y");
    // ModelBakery.MISSING_MODEL_LOCATION, without loading ModelBakery
    private static final ResourceLocation MISSING = new ResourceLocation("minecraft", "builtin/missing");
    private static final ResourceLocation PLATING = new ResourceLocation("bigreactors", "block/reactor/basic/assembledplating");

    @Test
    void mapsReplacedIdsToTheirBlocks() {

        final ModelReplacements replacements = new ModelReplacements(List.of(
                new FakeBuilder(List.of(CASING, GLASS), models -> {}),
                new FakeBuilder(List.of(ROD_X, ROD_Y), models -> {})));

        assertEquals(Set.of(new ResourceLocation("bigreactors", "basic_reactorcasing"),
                new ResourceLocation("bigreactors", "basic_reactorglass"),
                new ResourceLocation("bigreactors", "basic_reactorfuelrod")), replacements.blocks());
        assertTrue(replacements.replaces(CASING));
        assertTrue(replacements.replaces(ROD_Y));
        // the other states of a block keep their models, but Fabric must still load them with the replaced ones
        assertFalse(replacements.replaces(new ModelResourceLocation("bigreactors", "basic_reactorglass", "facings=all")));
        assertFalse(replacements.replaces(PLATING));
        assertFalse(replacements.replaces(new ModelResourceLocation("bigreactors", "basic_reactorcasing", "inventory")));
    }

    @Test
    void buildersSeeOriginalsAndEarlierReplacementsLikeForge() {

        final Map<ResourceLocation, BakedModel> made = new HashMap<>();
        // like BlockVariantsModelBuilder: reads its own block's original and a plain model, then replaces the block
        final FakeBuilder casing = new FakeBuilder(List.of(CASING), models -> {

            assertSame(original(CASING), models.get(CASING));
            assertSame(baked(PLATING), models.getOrDefault(PLATING, null));
            assertSame(baked(MISSING), models.get(MISSING));
            put(models, made, CASING, "casing replacement");
            // a builder sees its own replacement once it made it
            assertSame(made.get(CASING), models.get(CASING));
        });
        // like ReactorFuelRodModelBuilder: one model built from several originals, put under all of their ids
        final FakeBuilder rods = new FakeBuilder(List.of(ROD_X, ROD_Y), models -> {

            assertSame(original(ROD_X), models.get(ROD_X));
            assertSame(original(ROD_Y), models.get(ROD_Y));
            // an earlier builder's replacement, as in Forge's shared map; the lookup runs that builder on demand
            final BakedModel casingSeen = models.get(CASING);

            assertSame(made.get(CASING), casingSeen);

            final BakedModel rod = model("fuel rod");

            models.put(ROD_X, rod);
            models.put(ROD_Y, rod);
            made.put(ROD_X, rod);
        });
        // replaces again what the first builder replaced: it sees that replacement, not the original
        final FakeBuilder override = new FakeBuilder(List.of(CASING), models -> {

            assertSame(made.get(CASING), models.get(CASING));
            put(models, made, GLASS, "undeclared");
            models.put(CASING, model("casing override"));
        });

        final ModelReplacements replacements = new ModelReplacements(List.of(casing, rods, override));
        final ModelReplacements.Session session = replacements.newSession(ModelReplacementsTest::original,
                ModelReplacementsTest::baked);

        final BakedModel rodY = session.get(ROD_Y);

        assertEquals("fuel rod", rodY.toString());
        assertSame(made.get(ROD_X), rodY);
        assertSame(session.get(ROD_X), session.get(ROD_Y));
        assertEquals(1, casing.runs);
        assertEquals(1, rods.runs);
        // builders run on demand: nothing asked for the overriding builder's model yet
        assertEquals(0, override.runs);

        assertEquals("casing override", session.get(CASING).toString());
        assertEquals(1, override.runs);
        assertEquals(1, casing.runs);
        assertThrows(IllegalArgumentException.class, () -> session.get(GLASS));
    }

    @Test
    void aDeclaredModelLeftAloneKeepsWhatTheBuilderSaw() {

        final ModelReplacements replacements = new ModelReplacements(List.of(new FakeBuilder(List.of(CASING), models -> {})));

        assertSame(original(CASING), replacements.newSession(ModelReplacementsTest::original,
                ModelReplacementsTest::baked).get(CASING));
    }

    @Test
    void eachReloadRunsTheBuildersAgain() {

        final FakeBuilder builder = new FakeBuilder(List.of(CASING), models -> models.put(CASING, model("new")));
        final ModelReplacements replacements = new ModelReplacements(List.of(builder));

        final BakedModel first = replacements.newSession(ModelReplacementsTest::original, ModelReplacementsTest::baked).get(CASING);
        final BakedModel second = replacements.newSession(ModelReplacementsTest::original, ModelReplacementsTest::baked).get(CASING);

        assertEquals(2, builder.runs);
        assertFalse(first == second);
    }

    private static void put(final Map<ResourceLocation, BakedModel> models, final Map<ResourceLocation, BakedModel> made,
                            final ResourceLocation id, final String name) {

        final BakedModel model = model(name);

        models.put(id, model);
        made.put(id, model);
    }

    private static final Map<String, BakedModel> MODELS = new HashMap<>();

    private static BakedModel original(final ResourceLocation id) {
        return MODELS.computeIfAbsent("original " + id, ModelReplacementsTest::model);
    }

    private static BakedModel baked(final ResourceLocation id) {
        return MODELS.computeIfAbsent("baked " + id, ModelReplacementsTest::model);
    }

    /** A stand-in baked model; only its identity and name matter here. */
    private static BakedModel model(final String name) {
        return new NamedModel(name);
    }

    /**
     * A plain class rather than a {@link java.lang.reflect.Proxy}: a proxy initializes every class in the interface's
     * signatures, and {@code BlockState} cannot be initialized without bootstrapping the game.
     */
    private static final class NamedModel implements BakedModel {

        NamedModel(final String name) {
            this._name = name;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                        final RandomSource random) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean useAmbientOcclusion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGui3d() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean usesBlockLight() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCustomRenderer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemTransforms getTransforms() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemOverrides getOverrides() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return this._name;
        }

        private final String _name;
    }

    private static final class FakeBuilder implements ICustomModelBuilder {

        FakeBuilder(final List<ResourceLocation> replaced, final Consumer<Map<ResourceLocation, BakedModel>> bake) {

            this._replaced = new ArrayList<>(replaced);
            this._bake = bake;
        }

        @Override
        public void onRegisterModels(final Consumer<ResourceLocation> additionalModels) {
        }

        @Override
        public Collection<ResourceLocation> getReplacedModels() {
            return this._replaced;
        }

        @Override
        public void onBakeModels(final Map<ResourceLocation, BakedModel> models) {

            ++this.runs;
            this._bake.accept(models);
        }

        int runs;
        private final List<ResourceLocation> _replaced;
        private final Consumer<Map<ResourceLocation, BakedModel>> _bake;
    }
}
