/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

public final class ModifiableBiomeInfo {

    private ModifiableBiomeInfo() {
    }

    public record BiomeInfo() {

        public static class Builder {

            private final BiomeGenerationSettingsBuilder generationSettings = new BiomeGenerationSettingsBuilder();

            public BiomeGenerationSettingsBuilder getGenerationSettings() {
                return generationSettings;
            }
        }
    }
}
