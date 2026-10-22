/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

import java.util.Map;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;

public final class ModifiableStructureInfo {

    private ModifiableStructureInfo() {
    }

    public record StructureInfo() {

        public static class Builder {

            private final StructureSettingsBuilder structureSettings;

            public Builder(Map<MobCategory, StructureSpawnOverride> spawnOverrides) {
                this.structureSettings = new StructureSettingsBuilder(spawnOverrides);
            }

            public StructureSettingsBuilder getStructureSettings() {
                return structureSettings;
            }
        }
    }
}
