package ports.buildcraft.energy;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.SimplexNoise;

/**
 * Upstream {@code BiomeOilDesert}, {@code BiomeOilOcean} and the {@code GenLayerAddOil*} layers that
 * {@code BiomeInitializer} put over the overworld's biomes. Each layer replaces the biomes it may replace
 * wherever a seeded simplex noise field passes its threshold. The loaders' mixins install a state when a
 * server is created and apply it to what the overworld's {@code MultiNoiseBiomeSource} picks.
 */
public final class OilBiomes {
    public static final ResourceKey<Biome> OIL_DESERT = ResourceKey.create(Registry.BIOME_REGISTRY, BuildCraft.id("oil_desert"));
    public static final ResourceKey<Biome> OIL_OCEAN = ResourceKey.create(Registry.BIOME_REGISTRY, BuildCraft.id("oil_ocean"));
    /** Upstream's HOT, DRY and SANDY biome dictionary types. */
    public static final TagKey<Biome> DESERT_REPLACEABLE = TagKey.create(Registry.BIOME_REGISTRY, BuildCraft.id("oil_desert_replaceable"));
    /** Upstream's OCEAN biome dictionary type. */
    public static final TagKey<Biome> OCEAN_REPLACEABLE = TagKey.create(Registry.BIOME_REGISTRY, BuildCraft.id("oil_ocean_replaceable"));
    /** Upstream {@code BCEnergyConfig.excessiveBiomes}, whose default is the two oil biomes. */
    public static final TagKey<Biome> EXCESSIVE_OIL = TagKey.create(Registry.BIOME_REGISTRY, BuildCraft.id("excessive_oil"));
    /** Upstream's oil biomes extend the desert and ocean, so surface rules naming those apply to them too. */
    private static final Map<ResourceKey<Biome>, ResourceKey<Biome>> PARENTS = Map.of(OIL_DESERT, Biomes.DESERT, OIL_OCEAN, Biomes.OCEAN);
    /** Upstream {@code GenLayerBiomeReplacer.OFFSET_RANGE}. */
    private static final int OFFSET_RANGE = 500000;

    @Nullable private static volatile State current;

    private OilBiomes() {}

    /** One {@code GenLayerBiomeReplacer}. */
    public record Layer(TagKey<Biome> replaceable, Holder<Biome> biome, double noiseScale, double noiseThreshold) {}

    /**
     * The layers of one world's overworld biome source, as {@code BiomeInitializer} only wrapped the overworld's
     * layers: both share the noise offsets that upstream rolls from the world seed.
     */
    public record State(BiomeSource source, long seed, double xOffset, double zOffset, List<Layer> layers, List<Holder<Biome>> oilBiomes) {
        public static State create(RegistryAccess registries, long seed, BiomeSource source) {
            Registry<Biome> biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
            Holder<Biome> desert = biomes.getHolderOrThrow(OIL_DESERT);
            Holder<Biome> ocean = biomes.getHolderOrThrow(OIL_OCEAN);
            Random rand = new Random(seed);
            double xOffset = rand.nextInt(OFFSET_RANGE) - (OFFSET_RANGE / 2);
            double zOffset = rand.nextInt(OFFSET_RANGE) - (OFFSET_RANGE / 2);
            // BiomeInitializer stacks the desert layer over the ocean layer.
            List<Layer> layers = List.of(new Layer(OCEAN_REPLACEABLE, ocean, 0.0005, 0.9), new Layer(DESERT_REPLACEABLE, desert, 0.001, 0.7));
            return new State(source, seed, xOffset, zOffset, layers, List.of(desert, ocean));
        }

        /** Other dimensions keep their biomes, even when they reuse the overworld's biome source preset. */
        public boolean appliesTo(BiomeSource biomeSource) {
            return biomeSource == source;
        }

        public double noise(Layer layer, int blockX, int blockZ) {
            return SimplexNoise.noise((blockX + xOffset) * layer.noiseScale(), (blockZ + zOffset) * layer.noiseScale());
        }

        /** The biome at a block column once every layer has run over {@code biome}. */
        public Holder<Biome> replace(Holder<Biome> biome, int blockX, int blockZ) {
            for (Layer layer : layers) {
                if (!oilBiomes.contains(biome) && biome.is(layer.replaceable()) && noise(layer, blockX, blockZ) > layer.noiseThreshold()) {
                    biome = layer.biome();
                }
            }
            return biome;
        }

        /**
         * {@code biomes} with the oil biomes appended when it holds any biome that they replace. Feature
         * ordering follows this order, so it must not depend on hashing.
         */
        public Set<Holder<Biome>> withOilBiomes(Set<Holder<Biome>> biomes) {
            if (biomes.containsAll(oilBiomes) || biomes.stream().noneMatch(this::replaceable)) return biomes;
            Set<Holder<Biome>> all = new LinkedHashSet<>(biomes);
            all.addAll(oilBiomes);
            return Collections.unmodifiableSet(all);
        }

        private boolean replaceable(Holder<Biome> biome) {
            return layers.stream().anyMatch(layer -> biome.is(layer.replaceable()));
        }
    }

    /** A biome source's possible biomes under {@code state}, kept together so that threads never see them mismatched. */
    public record PossibleBiomes(State state, Set<Holder<Biome>> biomes) {}

    /** Installs the layers for a server's overworld, or none when it has no multi-noise overworld. */
    public static void install(RegistryAccess registries, long seed, @Nullable BiomeSource overworld) {
        install(overworld instanceof MultiNoiseBiomeSource ? State.create(registries, seed, overworld) : null);
    }

    public static void install(@Nullable State state) {
        current = state;
    }

    @Nullable
    public static State current() {
        return current;
    }

    /** The biomes that a surface rule names, with the oil biome of each parent among them. */
    public static Set<ResourceKey<Biome>> withOilBiomeKeys(Collection<ResourceKey<Biome>> biomes) {
        Set<ResourceKey<Biome>> keys = new HashSet<>(biomes);
        PARENTS.forEach((oil, parent) -> {
            if (keys.contains(parent)) keys.add(oil);
        });
        return Set.copyOf(keys);
    }
}
