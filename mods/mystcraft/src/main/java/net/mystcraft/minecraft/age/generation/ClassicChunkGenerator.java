package net.mystcraft.minecraft.age.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Source density equations and interpolation for versioned classic flat/void/Nether/End Ages. */
public final class ClassicChunkGenerator extends ChunkGenerator {
    public static final Codec<ClassicChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            commonCodec(instance).and(instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter((ClassicChunkGenerator generator) -> generator.biomeSource),
                    Codec.LONG.fieldOf("seed").forGetter((ClassicChunkGenerator generator) -> generator.seed),
                    ClassicTerrainSpec.CODEC.fieldOf("spec").forGetter((ClassicChunkGenerator generator) -> generator.spec),
                    ClassicGenerationPlan.CODEC.optionalFieldOf("generation_plan").forGetter((ClassicChunkGenerator generator) -> generator.generationPlan)
            )).apply(instance, ClassicChunkGenerator::new));
    private final Optional<ClassicGenerationPlan> generationPlan;
    private final long seed;
    private final ClassicTerrainSpec spec;
    private final BlockState fill, sea;
    private final ClassicDensity density;
    private final ClassicNormalDensity normalDensity;
    private final ClassicSurfaceNoise surfaceNoise;
    private final ClassicSpecializedSurface specializedSurface;
    private final java.util.Map<Long, double[]> latticeCache = new java.util.LinkedHashMap<>(8, 0.75F, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, double[]> entry) { return size() > 8; }
    };
    public ClassicChunkGenerator(Registry<StructureSet> structures, BiomeSource source, long seed, ClassicTerrainSpec spec) {
        this(structures,source,seed,spec,Optional.empty());
    }
    public ClassicChunkGenerator(Registry<StructureSet> structures, BiomeSource source,long seed,ClassicTerrainSpec spec,ClassicGenerationPlan plan) {
        this(structures,source,seed,spec,Optional.of(plan));
    }
    private ClassicChunkGenerator(Registry<StructureSet> structures,BiomeSource source,long seed,ClassicTerrainSpec spec,Optional<ClassicGenerationPlan> plan) {
        super(structures, Optional.empty(), source);
        this.generationPlan=plan;
        this.seed = seed; this.spec = spec;
        if (!Registry.BLOCK.containsKey(spec.fill()) || !Registry.BLOCK.containsKey(spec.sea()))
            throw new IllegalArgumentException("Saved classic terrain references missing blocks");
        fill = plan.isPresent()?plan.get().fill():Registry.BLOCK.get(spec.fill()).defaultBlockState(); sea = plan.isPresent()?plan.get().sea():Registry.BLOCK.get(spec.sea()).defaultBlockState();
        normalDensity = spec.version() >= 2 && (spec.terrain().equals("normal") || spec.terrain().equals("amplified")) ? new ClassicNormalDensity(seed, spec.terrain().equals("amplified"),
                (x, z) -> ClassicBiomeProfiles.profile(source instanceof ClassicBiomeSource classic ? classic.densityBiome(x,z) : source.getNoiseBiome(x, 0, z, net.minecraft.world.level.biome.Climate.empty()))) : null;
        specializedSurface = spec.version() >= 4 ? new ClassicSpecializedSurface(seed) : null;
        surfaceNoise = spec.version() >= 2 && (spec.terrain().equals("normal") || spec.terrain().equals("amplified")) ? new ClassicSurfaceNoise(seed) : null;
        density = spec.terrain().equals("nether") || spec.terrain().equals("end") ? new ClassicDensity(seed, spec.terrain().equals("end")) : null;
    }
    /** Frozen plans own authored population; older generators retain compatibility biome features. */
    public boolean usesAuthoredPopulation(){return generationPlan.isPresent();}
    @Override public java.util.stream.Stream<net.minecraft.core.Holder<StructureSet>> possibleStructureSets(){
        if(generationPlan==null||generationPlan.isEmpty())return super.possibleStructureSets();
        return ClassicStructures.select(super.possibleStructureSets(),generationPlan.get());
    }
    @Override public void createStructures(net.minecraft.core.RegistryAccess registries,RandomState random,StructureManager structures,ChunkAccess chunk,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,long worldSeed){
        super.createStructures(registries,random,structures,chunk,templates,worldSeed);
        generationPlan.ifPresent(plan->ClassicStructures.createFortress(this,plan,registries,random,structures,chunk,templates,worldSeed));
    }
    @Override public com.mojang.datafixers.util.Pair<BlockPos,net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> findNearestMapStructure(net.minecraft.server.level.ServerLevel level,net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> targets,BlockPos origin,int radius,boolean skipReferenced){
        var found=super.findNearestMapStructure(level,targets,origin,radius,skipReferenced);
        return found==null&&generationPlan.isPresent()?ClassicStructures.locateFortress(this,generationPlan.get(),level,targets,origin,radius,skipReferenced):found;
    }
    public ClassicTerrainSpec specification() { return spec; }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 256; }
    @Override public int getMinY() { return 0; }
    @Override public int getSeaLevel() { return spec.seaLevel(); }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // Source ChunkProviderMyst runs biome surfaces first, then ordered terrain alterations.
        if(step==GenerationStep.Carving.AIR)applyClassicAlterations(chunk);
    }
    private void applyClassicAlterations(ChunkAccess chunk) {
        if(generationPlan.isPresent()) {
            for(var alteration:generationPlan.get().alterations()) {
                if(alteration.kind().equals("caves"))new ClassicCaves(alteration.seed(),15,40,Blocks.AIR).generate(chunk);
                else if(alteration.kind().equals("ravines"))new ClassicRavines(alteration.seed(),Blocks.AIR).generate(chunk);
                else if(alteration.kind().equals("floatislands")) {
                    if(!(biomeSource instanceof ClassicBiomeSource source))throw new IllegalStateException("Islands require frozen biome source");
                    new ClassicFloatingIslands(alteration.seed(),alteration.state(),source.biome(new net.minecraft.resources.ResourceLocation(alteration.material().getString("Biome")))).generate(chunk);
                }
                else if(alteration.kind().equals("hugetrees"))new ClassicHugeTrees(alteration.seed()).generate(chunk);
                else if(alteration.kind().equals("spheres"))new ClassicSpheres(alteration.seed(),alteration.state()).generate(chunk);
                else if(alteration.kind().equals("tendrils"))new ClassicCaves(alteration.seed(),15,18,alteration.state()).generate(chunk);
            }
            if(generationPlan.get().alterations().stream().anyMatch(a->a.kind().equals("skylands")))ClassicSkylands.filter(chunk,seed);
            Heightmap.primeHeightmaps(chunk,java.util.Set.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
        }
    }
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        if (normalDensity != null) ClassicNormalSurface.build(chunk, spec.seaLevel(), surfaceNoise.field(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ()),specializedSurface);
        else if (!spec.terrain().equals("void")) ClassicSurface.build(chunk, fill, spec.seaLevel());
        if(generationPlan.isPresent()&&generationPlan.get().alterations().stream().anyMatch(a->a.kind().equals("skylands")))ClassicSkylands.filter(chunk,seed);
    }
    @Override public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level,ChunkAccess chunk,StructureManager structures) {
        super.applyBiomeDecoration(level,chunk,structures);
        generationPlan.ifPresent(plan->{ClassicPopulation.populate(level,this,chunk.getPos(),seed,plan,structures);if(plan.alterations().stream().anyMatch(a->a.kind().equals("skylands")))ClassicSkylands.filter(chunk,seed);});
    }
    @Override public void spawnOriginalMobs(WorldGenRegion region) {
        if (spec.terrain().equals("void")) return;
        var random = new LegacyRandomSource(seed ^ region.getCenter().toLong());
        BlockPos sample = region.getCenter().getWorldPosition().offset(8, spec.ground(), 8);
        NaturalSpawner.spawnMobsForChunkGeneration(region, region.getBiome(sample), region.getCenter(), random);
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                                StructureManager structures, ChunkAccess chunk) {
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BlockState[] column = column(baseX + x, baseZ + z);
            for (int y = 0; y < column.length; y++) {
                BlockState state = column[y];
                if (state.isAir()) continue;
                pos.set(baseX + x, y, baseZ + z); chunk.setBlockState(pos, state, false);
                surface.update(x, y, z, state); floor.update(x, y, z, state);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        BlockState[] column = column(x, z);
        for (int y = Math.min(255, height.getMaxBuildHeight() - 1); y >= Math.max(0, height.getMinBuildHeight()); y--)
            if (type.isOpaque().test(column[y])) return y + 1;
        return Math.max(0, height.getMinBuildHeight());
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) { return new NoiseColumn(0, column(x, z)); }
    @Override public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
        lines.add("Mystcraft classic terrain v" + spec.version() + ": " + spec.terrain());
    }
    /** Pure deterministic base column, also used by the runtime/source math tests. */
    public BlockState[] column(int worldX, int worldZ) {
        BlockState[] result = new BlockState[256]; java.util.Arrays.fill(result, Blocks.AIR.defaultBlockState());
        if (spec.terrain().equals("void")) return result;
        int localX = worldX & 15, localZ = worldZ & 15;
        double[] field = density == null && normalDensity == null ? null : lattice(worldX >> 4, worldZ >> 4);
        for (int y = 0; y < (field == null ? 256 : 128); y++) {
            if (spec.terrain().equals("flat")) {
                result[y] = y == 0 && spec.bedrock() ? Blocks.BEDROCK.defaultBlockState()
                        : y < spec.ground() ? fill : y <= spec.seaLevel() ? sea : Blocks.AIR.defaultBlockState();
            } else {
                // A coordinate-seeded floor removes the legacy shared-RNG generation-order bug.
                // Its per-height probabilities match y <= nextInt(5), while chunk order is stable.
                long hash = seed ^ worldX * 341873128712L ^ worldZ * 132897987541L ^ y * 42317861L;
                hash ^= hash >>> 33; hash *= 0xff51afd7ed558ccdL; hash ^= hash >>> 33;
                if (spec.bedrock() && y < 5 && y <= Math.floorMod(hash, 5)) result[y] = Blocks.BEDROCK.defaultBlockState();
                else if (ClassicDensity.interpolate(field, localX, y, localZ) > 0) result[y] = fill;
                else if (y < spec.seaLevel()) result[y] = sea;
            }
        }
        return result;
    }
    private double[] lattice(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        synchronized (latticeCache) { return latticeCache.computeIfAbsent(key, ignored -> normalDensity != null ? normalDensity.lattice(chunkX, chunkZ) : density.lattice(chunkX, chunkZ)); }
    }
}
