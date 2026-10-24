/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Collects the features a biome modifier adds; the Fabric adapter hands them to Fabric's biome modification context.
 */
public class BiomeGenerationSettingsBuilder {

    private final List<AddedFeature> addedFeatures = new ArrayList<>();

    public BiomeGenerationSettingsBuilder addFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature) {
        addedFeatures.add(new AddedFeature(step, feature));
        return this;
    }

    public List<AddedFeature> getAddedFeatures() {
        return addedFeatures;
    }

    public record AddedFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature) {
    }
}
