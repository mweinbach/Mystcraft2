/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import org.jetbrains.annotations.Nullable;

/**
 * A mutable copy of a structure's spawn overrides. The baby-mob modifier appends to the monster override.
 */
public final class StructureSettingsBuilder {

    private final Map<MobCategory, StructureSpawnOverrideBuilder> overrides = new EnumMap<>(MobCategory.class);

    public StructureSettingsBuilder(Map<MobCategory, StructureSpawnOverride> original) {
        for (Map.Entry<MobCategory, StructureSpawnOverride> entry : original.entrySet()) {
            overrides.put(entry.getKey(), new StructureSpawnOverrideBuilder(entry.getValue()));
        }
    }

    @Nullable
    public StructureSpawnOverrideBuilder getSpawnOverrides(MobCategory category) {
        return overrides.get(category);
    }

    public static final class StructureSpawnOverrideBuilder {

        private final List<MobSpawnSettings.SpawnerData> spawns;

        public StructureSpawnOverrideBuilder(StructureSpawnOverride override) {
            this.spawns = new ArrayList<>(override.spawns().unwrap());
        }

        public List<MobSpawnSettings.SpawnerData> getSpawns() {
            return Collections.unmodifiableList(spawns);
        }

        public void addSpawn(MobSpawnSettings.SpawnerData data) {
            spawns.add(data);
        }
    }
}
