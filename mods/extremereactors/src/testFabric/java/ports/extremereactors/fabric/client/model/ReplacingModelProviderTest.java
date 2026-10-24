/*
 * Extreme Reactors 1.19.2 port - Fabric adapter tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client.model;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import ports.extremereactors.corelib.client.model.ICustomModelBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tekxit 4 runs ModernFix with dynamic resources, which loads block state models only when something bakes them. A
 * model builder can then ask for the original of a block Minecraft has not loaded yet; the provider used to hand that
 * id back to the bakery, which baked it through the same builder again ("Model builder 12 depends on itself").
 */
class ReplacingModelProviderTest {

    private static final ModelResourceLocation CASING = new ModelResourceLocation("bigreactors", "reprocessorcasing", "");
    private static final ModelResourceLocation CONTROLLER = new ModelResourceLocation("bigreactors", "reprocessorcontroller", "facing=north");

    @Test
    void loadsTheBlockOfAnOriginalNothingHasLoadedYet() {

        final UnbakedModel controller = new FakeUnbaked();
        final List<ResourceLocation> loaded = new ArrayList<>();
        final ReplacingModelProvider provider = new ReplacingModelProvider(
                new ModelReplacements(List.of(new Declares(List.of(CASING, CONTROLLER)))),
                blockId -> {
                    loaded.add(blockId);
                    return blockId.getPath().equals("reprocessorcontroller") ? Map.of(CONTROLLER, controller) : Map.of();
                });

        assertSame(controller, provider.originalOf(CONTROLLER));
        assertSame(controller, provider.originalOf(CONTROLLER));
        assertNull(provider.originalOf(CASING));
        // every block is read from the resources once
        assertEquals(List.of(new ResourceLocation("bigreactors", "reprocessorcontroller"),
                new ResourceLocation("bigreactors", "reprocessorcasing")), loaded);
    }

    private record Declares(List<ResourceLocation> ids) implements ICustomModelBuilder {

        @Override
        public void onRegisterModels(final Consumer<ResourceLocation> additionalModels) {
        }

        @Override
        public Collection<ResourceLocation> getReplacedModels() {
            return this.ids;
        }

        @Override
        public void onBakeModels(final Map<ResourceLocation, BakedModel> models) {
        }
    }

    private static final class FakeUnbaked implements UnbakedModel {

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return List.of();
        }

        @Override
        public Collection<Material> getMaterials(final Function<ResourceLocation, UnbakedModel> models,
                                                 final Set<Pair<String, String>> missingTextures) {
            return List.of();
        }

        @Override
        public BakedModel bake(final ModelBakery bakery, final Function<Material, TextureAtlasSprite> sprites,
                               final ModelState state, final ResourceLocation location) {
            throw new UnsupportedOperationException();
        }
    }
}
