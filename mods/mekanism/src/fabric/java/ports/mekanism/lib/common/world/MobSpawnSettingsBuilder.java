/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jetbrains.annotations.Nullable;

/**
 * A mutable copy of a biome's mob spawns. Additions' baby-mob modifier reads the parent spawns and appends the babies.
 */
public final class MobSpawnSettingsBuilder {

    private final MobSpawnSettings original;
    private final Map<MobCategory, List<MobSpawnSettings.SpawnerData>> spawners = new EnumMap<>(MobCategory.class);
    private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> ownCosts = new HashMap<>();

    public MobSpawnSettingsBuilder(MobSpawnSettings original) {
        this.original = original;
        for (MobCategory category : MobCategory.values()) {
            spawners.put(category, new ArrayList<>(original.getMobs(category).unwrap()));
        }
    }

    public List<MobSpawnSettings.SpawnerData> getSpawner(MobCategory category) {
        return spawners.computeIfAbsent(category, key -> new ArrayList<>());
    }

    public void addSpawn(MobCategory category, MobSpawnSettings.SpawnerData data) {
        getSpawner(category).add(data);
    }

    @Nullable
    public MobSpawnSettings.MobSpawnCost getCost(EntityType<?> type) {
        MobSpawnSettings.MobSpawnCost cost = ownCosts.get(type);
        return cost != null ? cost : original.getMobSpawnCost(type);
    }

    public boolean hasOwnCost(EntityType<?> type) {
        return ownCosts.containsKey(type);
    }

    public void addMobCharge(EntityType<?> type, double charge, double energyBudget) {
        ownCosts.put(type, new MobSpawnSettings.MobSpawnCost(energyBudget, charge));
    }
}
