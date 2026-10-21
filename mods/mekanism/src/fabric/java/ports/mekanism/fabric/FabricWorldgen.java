/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import mekanism.common.Mekanism;
import mekanism.common.resource.ore.OreType;
import mekanism.common.world.modifier.MekanismOreBiomeModifier;
import mekanism.common.world.modifier.MekanismSaltBiomeModifier;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.mekanism.lib.common.world.BiomeGenerationSettingsBuilder.AddedFeature;
import ports.mekanism.lib.common.world.BiomeModifier;
import ports.mekanism.lib.common.world.ModifiableBiomeInfo;

/**
 * Applies Mekanism's {@code data/mekanism/forge/biome_modifier} files through Fabric's biome modification API.
 * The modifier records are the ones Forge uses, so each file adds those shared placed features for its biomes
 * and decoration step.
 */
public final class FabricWorldgen {

    private FabricWorldgen() {
    }

    public static void register() {
        List<LoadedModifier> modifiers = loadModifiers();
        if (modifiers.isEmpty()) {
            throw new IllegalStateException("Mekanism shipped no biome modifiers");
        }
        for (LoadedModifier modifier : modifiers) {
            BiomeModifications.create(new ResourceLocation(Mekanism.MODID, "biome_modifier/" + modifier.id()))
                  .add(ModificationPhase.ADDITIONS, modifier::matches, (selection, context) -> modifier.apply(selection, context.getGenerationSettings()));
        }
        Mekanism.logger.info("Attached {} Mekanism biome modifiers through Fabric's biome modifications", modifiers.size());
    }

    private static List<LoadedModifier> loadModifiers() {
        Path root = FabricLoader.getInstance().getModContainer(Mekanism.MODID)
              .orElseThrow(() -> new IllegalStateException("Mekanism mod container is missing"))
              .findPath("data/mekanism/forge/biome_modifier")
              .orElseThrow(() -> new IllegalStateException("Mekanism biome modifiers are missing from the mod"));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read Mekanism biome modifiers", e);
        }
        List<LoadedModifier> modifiers = new ArrayList<>();
        for (Path file : files) {
            modifiers.add(read(root, file));
        }
        return List.copyOf(modifiers);
    }

    private static LoadedModifier read(Path root, Path file) {
        String id = root.relativize(file).toString().replace('\\', '/');
        id = id.substring(0, id.length() - ".json".length());
        JsonObject json;
        try {
            json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not read biome modifier " + file, e);
        }
        ResourceLocation type = new ResourceLocation(required(json, "type", file).getAsString());
        BiomeSelector biomes = biomes(required(json, "biomes", file), file);
        Decoration step = decode(Decoration.CODEC, required(json, "step", file), file, "step");
        ResourceLocation ore = new ResourceLocation(Mekanism.MODID, "ore");
        ResourceLocation salt = new ResourceLocation(Mekanism.MODID, "salt");
        if (type.equals(ore)) {
            OreType oreType = decode(OreType.CODEC, required(json, "oreType", file), file, "oreType");
            return new LoadedModifier(id, biomes, step, Optional.of(oreType));
        }
        if (type.equals(salt)) {
            return new LoadedModifier(id, biomes, step, Optional.empty());
        }
        throw new IllegalStateException("Biome modifier " + file + " has unknown type " + type);
    }

    private static JsonElement required(JsonObject json, String field, Path file) {
        JsonElement element = json.get(field);
        if (element == null) {
            throw new IllegalStateException("Biome modifier " + file + " is missing " + field);
        }
        return element;
    }

    private static <T> T decode(Codec<T> codec, JsonElement json, Path file, String field) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
        Optional<T> parsed = result.result();
        if (parsed.isEmpty()) {
            String message = result.error().map(DataResult.PartialResult::message).orElse("no value");
            throw new IllegalStateException("Biome modifier " + file + " has " + field + " " + json + ": " + message);
        }
        return parsed.get();
    }

    private static BiomeSelector biomes(JsonElement element, Path file) {
        List<TagKey<Biome>> tags = new ArrayList<>();
        List<ResourceKey<Biome>> keys = new ArrayList<>();
        addBiomes(element, tags, keys, file);
        if (tags.isEmpty() && keys.isEmpty()) {
            throw new IllegalStateException("Biome modifier " + file + " selects no biomes");
        }
        return new BiomeSelector(List.copyOf(tags), List.copyOf(keys));
    }

    private static void addBiomes(JsonElement element, List<TagKey<Biome>> tags, List<ResourceKey<Biome>> keys, Path file) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                addBiomes(child, tags, keys, file);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("tag")) {
                tags.add(TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(object.get("tag").getAsString())));
                return;
            }
            if (object.has("values")) {
                addBiomes(object.get("values"), tags, keys, file);
                return;
            }
            throw new IllegalStateException("Biome modifier " + file + " has an unrecognised biomes value " + object);
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException("Biome modifier " + file + " has an unrecognised biomes value " + element);
        }
        String text = element.getAsString();
        if (text.startsWith("#")) {
            tags.add(TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(text.substring(1))));
        } else {
            keys.add(ResourceKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(text)));
        }
    }

    private record BiomeSelector(List<TagKey<Biome>> tags, List<ResourceKey<Biome>> biomes) {

        private boolean matches(BiomeSelectionContext context) {
            for (TagKey<Biome> tag : tags) {
                if (context.hasTag(tag)) {
                    return true;
                }
            }
            return biomes.contains(context.getBiomeKey());
        }
    }

    /**
     * {@code oreType} is empty for the salt modifier. The biome set passed into the shared modifier contains only the
     * biome Fabric has already selected, so {@code modify} contributes that modifier's placed features and nothing else.
     */
    private record LoadedModifier(String id, BiomeSelector biomes, Decoration step, Optional<OreType> oreType) {

        private boolean matches(BiomeSelectionContext context) {
            return biomes.matches(context);
        }

        private void apply(BiomeSelectionContext selection, BiomeModificationContext.GenerationSettingsContext generation) {
            Holder<Biome> biome = selection.getBiomeRegistryEntry();
            HolderSet<Biome> justThisBiome = HolderSet.direct(biome);
            BiomeModifier modifier = oreType
                  .<BiomeModifier>map(type -> new MekanismOreBiomeModifier(justThisBiome, step, type))
                  .orElseGet(() -> new MekanismSaltBiomeModifier(justThisBiome, step));
            ModifiableBiomeInfo.BiomeInfo.Builder builder = new ModifiableBiomeInfo.BiomeInfo.Builder();
            modifier.modify(biome, BiomeModifier.Phase.ADD, builder);
            List<AddedFeature> added = builder.getGenerationSettings().getAddedFeatures();
            if (added.isEmpty()) {
                throw new IllegalStateException("Biome modifier " + id + " added no features to " + selection.getBiomeKey().location());
            }
            for (AddedFeature feature : added) {
                generation.addFeature(feature.step(), feature.feature().unwrapKey().orElseThrow(
                      () -> new IllegalStateException("Placed feature from biome modifier " + id + " has no registry key")));
            }
        }
    }
}
