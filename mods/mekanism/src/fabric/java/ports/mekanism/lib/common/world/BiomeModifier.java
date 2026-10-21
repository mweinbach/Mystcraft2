/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * A data-driven change to biomes, read from {@code data/<namespace>/forge/biome_modifier}. On Fabric the port applies
 * Mekanism's modifiers through Fabric's biome modification API.
 */
public interface BiomeModifier {

    void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder);

    Codec<? extends BiomeModifier> codec();

    enum Phase {
        BEFORE_EVERYTHING,
        ADD,
        REMOVE,
        MODIFY,
        AFTER_EVERYTHING
    }
}
