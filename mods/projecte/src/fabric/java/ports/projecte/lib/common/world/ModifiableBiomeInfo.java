/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.world;

public final class ModifiableBiomeInfo {

    private ModifiableBiomeInfo() {
    }

    public record BiomeInfo() {

        public static class Builder {

            private final BiomeGenerationSettingsBuilder generationSettings = new BiomeGenerationSettingsBuilder();
            private final MobSpawnSettingsBuilder mobSpawnSettings;

            public Builder() {
                this(null);
            }

            /**
             * A builder that can also change mob spawns. Ore modifiers use the no-arg constructor and never read spawns.
             */
            public Builder(MobSpawnSettingsBuilder mobSpawnSettings) {
                this.mobSpawnSettings = mobSpawnSettings;
            }

            public BiomeGenerationSettingsBuilder getGenerationSettings() {
                return generationSettings;
            }

            public MobSpawnSettingsBuilder getMobSpawnSettings() {
                return mobSpawnSettings;
            }
        }
    }
}
