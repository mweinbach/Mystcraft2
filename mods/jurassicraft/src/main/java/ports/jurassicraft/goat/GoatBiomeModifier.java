package ports.jurassicraft.goat;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import ports.jurassicraft.config.JurassicConfig;

/** Matches upstream pig-biome spawning, disabled by default. */
public final class GoatBiomeModifier implements BiomeModifier {
    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !JurassicConfig.NATURAL_GOAT_SPAWNING.get()) return;
        var spawns = builder.getMobSpawnSettings().getSpawner(MobCategory.CREATURE);
        if (spawns.stream().anyMatch(spawn -> spawn.type == EntityType.PIG)) {
            spawns.add(new MobSpawnSettings.SpawnerData(GoatRegistry.GOAT.get(), 10, 2, 4));
        }
    }
    @Override public Codec<? extends BiomeModifier> codec() { return GoatRegistry.SPAWN_MODIFIER.get(); }
}
