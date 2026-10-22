package ports.jurassicraft.structures;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;

/**
 * One seed-derived candidate in jungle or savanna. Extending random spread lets
 * {@code findNearestMapStructure} see it; every search cell still names that one chunk.
 */
public final class VisitorPlacement extends RandomSpreadStructurePlacement {
    public static final Codec<VisitorPlacement> CODEC = Codec.unit(VisitorPlacement::new);
    private static final int SEARCH_SPACING = 32;
    private static final TagKey<Biome> TROPICAL = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(PortMod.MOD_ID,"has_structure/raptor_paddock"));
    private static final Map<ChunkGenerator, Map<Long, Optional<ChunkPos>>> CACHE = new WeakHashMap<>();
    /** Present candidates only, so a dimension without the biome does not hide the overworld start. */
    private static final Map<Long, ChunkPos> FOUND = new ConcurrentHashMap<>();

    public VisitorPlacement() {
        super(SEARCH_SPACING, SEARCH_SPACING - 1, RandomSpreadType.LINEAR, 0);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGenerator generator, RandomState state, long seed, int x, int z) {
        return candidate(generator, state, seed).filter(pos -> pos.x == x && pos.z == z).isPresent();
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        ChunkPos found = FOUND.get(seed);
        return found != null ? found : ChunkPos.ZERO;
    }

    /** Candidate only; callers must still verify that a valid structure start was generated. */
    public static Optional<ChunkPos> candidate(ServerLevel level) {
        return candidate(level, JurassicConfig.GENERATE_VISITOR_CENTRE.get());
    }
    /** Candidate for new structure generation, using the caller's generation setting. */
    public static Optional<ChunkPos> candidate(ServerLevel level, boolean generationEnabled) {
        return candidate(level, generationEnabled, false);
    }
    /** Candidate for a structure search, including already generated starts after generation is disabled. */
    public static Optional<ChunkPos> candidateForLocate(ServerLevel level) {
        return candidate(level, false, true);
    }
    static Optional<ChunkPos> candidate(ServerLevel level, boolean generationEnabled, boolean includeExisting) {
        if (!includeExisting && (!level.dimension().equals(Level.OVERWORLD)
                || !level.getServer().getWorldData().worldGenSettings().generateStructures() || !generationEnabled)) return Optional.empty();
        var chunks = level.getChunkSource();
        return candidate(chunks.getGenerator(), chunks.randomState(), level.getSeed());
    }
    private static Optional<ChunkPos> candidate(ChunkGenerator generator, RandomState state, long seed) {
        synchronized (CACHE) {
            Optional<ChunkPos> chosen = CACHE.computeIfAbsent(generator, ignored -> new HashMap<>())
                    .computeIfAbsent(seed, ignored -> choose(generator, state, seed));
            chosen.ifPresent(pos -> FOUND.put(seed, pos));
            return chosen;
        }
    }
    private static Optional<ChunkPos> choose(ChunkGenerator generator, RandomState state, long seed) {
        if (generator.getBiomeSource().possibleBiomes().stream().noneMatch(biome -> biome.is(TROPICAL)))
            return Optional.empty();
        Random random=new Random(seed);
        for (int i=0;i<10000;i++) {
            int x=random.nextInt(5000)+(random.nextBoolean()?5000:-5000);
            int z=random.nextInt(5000)+(random.nextBoolean()?5000:-5000);
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),state.sampler());
            if (biome.is(TROPICAL)) return Optional.of(new ChunkPos(Math.floorDiv(x,16),Math.floorDiv(z,16)));
        }
        return Optional.empty();
    }
    @Override public StructurePlacementType<?> type() { return StructureRegistry.VISITOR_PLACEMENT.get(); }
}
