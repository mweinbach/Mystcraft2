package ports.jurassicraft.structures;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Random;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;

/** One seed-derived candidate for each world, selected in jungle or savanna. */
public final class VisitorPlacement extends StructurePlacement {
    public static final Codec<VisitorPlacement> CODEC = Codec.unit(VisitorPlacement::new);
    private static final TagKey<Biome> TROPICAL = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(PortMod.MOD_ID,"has_structure/raptor_paddock"));
    private static final Map<ChunkGenerator, Map<Long, Optional<ChunkPos>>> CACHE = new WeakHashMap<>();
    public VisitorPlacement() { super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1, 0, Optional.empty()); }
    @Override protected boolean isPlacementChunk(ChunkGenerator generator, RandomState state, long seed, int x, int z) {
        return candidate(generator, state, seed).filter(pos -> pos.x == x && pos.z == z).isPresent();
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
            return CACHE.computeIfAbsent(generator, ignored -> new HashMap<>())
                    .computeIfAbsent(seed, ignored -> choose(generator, state, seed));
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
