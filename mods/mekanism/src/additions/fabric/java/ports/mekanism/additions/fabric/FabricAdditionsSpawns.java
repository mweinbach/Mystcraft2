/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.additions.fabric;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.entity.baby.BabyType;
import mekanism.additions.common.world.modifier.BabyEntitySpawnBiomeModifier;
import ports.mekanism.lib.fml.config.ConfigTracker;
import ports.mekanism.lib.fml.config.ModConfig;
import ports.mekanism.lib.fml.loading.FMLPaths;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import ports.mekanism.lib.common.world.BiomeModifier;
import ports.mekanism.lib.common.world.MobSpawnSettingsBuilder;
import ports.mekanism.lib.common.world.ModifiableBiomeInfo;

/**
 * Forge loads {@code data/mekanismadditions/forge/biome_modifier} itself. Each file names one baby type and adds that
 * baby wherever the parent already spawns. Fabric runs those same modifiers through biome modifications.
 */
public final class FabricAdditionsSpawns {

    private FabricAdditionsSpawns() {
    }

    /**
     * Fabric bakes biome spawns inside the server constructor, before the server-starting event loads world configs.
     * The additions server config is opened here so the baby-mob weights match the world's file.
     */
    public static void prepareServerConfig(Path serverConfigs) {
        Path defaults = FMLPaths.GAMEDIR.get().resolve("defaultconfigs");
        for (ModConfig config : ConfigTracker.INSTANCE.configs(ModConfig.Type.SERVER)) {
            if (!MekanismAdditions.MODID.equals(config.getModId())) {
                continue;
            }
            Path source = defaults.resolve(config.getFileName());
            Path target = serverConfigs.resolve(config.getFileName());
            if (Files.isRegularFile(source) && !Files.exists(target)) {
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to copy the default config " + source, e);
                }
            }
            ConfigTracker.INSTANCE.loadConfig(config, serverConfigs);
        }
    }

    public static void register() {
        BiomeModifications.create(new ResourceLocation(MekanismAdditions.MODID, "baby_mobs"))
              .add(ModificationPhase.REMOVALS, context -> true, FabricAdditionsSpawns::apply);
    }

    private static void apply(BiomeSelectionContext selection, BiomeModificationContext context) {
        Holder<Biome> biome = selection.getBiomeRegistryEntry();
        MobSpawnSettingsBuilder spawns = new MobSpawnSettingsBuilder(selection.getBiome().getMobSettings());
        Set<EntityType<?>> existing = new HashSet<>();
        for (MobSpawnSettings.SpawnerData data : spawns.getSpawner(MobCategory.MONSTER)) {
            existing.add(data.type);
        }
        ModifiableBiomeInfo.BiomeInfo.Builder builder = new ModifiableBiomeInfo.BiomeInfo.Builder(spawns);
        for (BabyType type : BabyType.values()) {
            new BabyEntitySpawnBiomeModifier(type).modify(biome, BiomeModifier.Phase.REMOVE, builder);
        }
        BiomeModificationContext.SpawnSettingsContext spawnContext = context.getSpawnSettings();
        for (MobSpawnSettings.SpawnerData data : spawns.getSpawner(MobCategory.MONSTER)) {
            if (existing.contains(data.type)) {
                continue;
            }
            spawnContext.addSpawn(MobCategory.MONSTER, data);
            if (spawns.hasOwnCost(data.type)) {
                MobSpawnSettings.MobSpawnCost cost = spawns.getCost(data.type);
                spawnContext.setSpawnCost(data.type, cost.getCharge(), cost.getEnergyBudget());
            }
        }
    }
}
