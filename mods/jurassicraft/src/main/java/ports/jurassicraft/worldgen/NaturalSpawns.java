package ports.jurassicraft.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurHerd;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.goat.GoatRegistry;

/** Opt-in natural spawns from the original mod; loaders add each entry to the biomes it accepts. */
public final class NaturalSpawns {
    /** The parts of a biome a spawn rule may inspect, independent of each loader's biome API. */
    public interface BiomeView {
        boolean is(TagKey<Biome> tag);
        boolean spawns(MobCategory category, EntityType<?> type);
    }
    public interface Rule { boolean accepts(BiomeView biome); }
    public record Entry(EntityType<?> type, MobCategory category, int weight, int minCount, int maxCount, Rule rule) {}

    private NaturalSpawns() {}

    /** Whether a loader adds {@code entry} to {@code biome}: its rule accepts the biome, which does not spawn the type yet. */
    public static boolean adds(Entry entry, BiomeView biome) {
        return entry.rule().accepts(biome) && !biome.spawns(entry.category(), entry.type());
    }

    /** Reads the common config and registries; call once both are ready. */
    public static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        if (JurassicConfig.NATURAL_GOAT_SPAWNING.get()) {
            // As upstream, follow the actual pig spawn list so datapack and modded pig biomes work too.
            entries.add(new Entry(GoatRegistry.GOAT.get(), MobCategory.CREATURE, 10, 2, 4,
                    biome -> biome.spawns(MobCategory.CREATURE, EntityType.PIG)));
        }
        if (JurassicConfig.NATURAL_DINOSAUR_SPAWNING.get()) {
            for (Species species : Species.values()) {
                MobCategory category = species == Species.COELACANTH ? MobCategory.WATER_CREATURE : MobCategory.CREATURE;
                int maxGroup = Math.max(1, Math.min(6, DinosaurHerd.maxSize(species) / 2));
                TagKey<Biome> habitat = WorldgenPlacements.tag("spawns/" + species.id());
                entries.add(new Entry(DinosaurRegistry.type(species), category, weight(species), 1, maxGroup, biome -> biome.is(habitat)));
            }
        }
        return entries;
    }

    static int weight(Species species) {
        return switch (species) {
            case COELACANTH, BRACHIOSAURUS, TYRANNOSAURUS -> 5;
            case MUSSAURUS, PARASAUROLOPHUS -> 15;
            case GALLIMIMUS -> 25;
            default -> 10;
        };
    }
}
