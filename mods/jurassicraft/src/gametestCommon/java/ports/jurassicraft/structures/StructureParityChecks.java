package ports.jurassicraft.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.Optional;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;

/** Placement contracts that do not depend on a loader test harness. */
public final class StructureParityChecks {
    private StructureParityChecks() {}

    public static void paddockRollsPerChunk(GameTestHelper helper) {
        long seed = 0x5EEDL;
        PaddockPlacement always = new PaddockPlacement(1);
        PaddockPlacement coin = new PaddockPlacement(2);
        PaddockPlacement common = new PaddockPlacement(50);
        PaddockPlacement normal = new PaddockPlacement(4000);
        require(helper, always.spacing() == 1 && always.separation() == 0 && normal.spacing() == 1,
                "paddock placement must not thin chunks with a spacing grid");
        int alwaysHits = 0;
        for (int i = -32; i < 32; i++) if (always.isPlacementChunk(null, null, seed, i, -i - 3)) alwaysHits++;
        require(helper, alwaysHits == 64, "rarity 1 must accept every chunk");
        int coinHits = count(coin, seed, 4000);
        require(helper, coinHits > 1700 && coinHits < 2300, "rarity 2 must stay near one half, got " + coinHits);
        boolean adjacent = false;
        for (int x = 0; x < 400 && !adjacent; x++)
            adjacent = coin.isPlacementChunk(null, null, seed, x, 7) && coin.isPlacementChunk(null, null, seed, x + 1, 7);
        require(helper, adjacent, "neighbouring chunks must both be allowed to spawn");
        int commonHits = count(common, seed, 10000);
        require(helper, commonHits > 140 && commonHits < 270, "rarity 50 must stay near 1/50, got " + commonHits + " of 10000");
        int rareHits = count(normal, seed, 200000);
        require(helper, rareHits > 20 && rareHits < 80, "rarity 4000 must stay near 1/4000, got " + rareHits + " of 200000");
        boolean first = normal.isPlacementChunk(null, null, seed, -100, 200);
        require(helper, first == normal.isPlacementChunk(null, null, seed, -100, 200), "the same chunk must roll the same way");
        var set = helper.getLevel().registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY)
                .get(new ResourceLocation(PortMod.MOD_ID, "raptor_paddock"));
        require(helper, set != null && set.placement() instanceof PaddockPlacement placement
                        && placement.rarity() == JurassicConfig.RAPTOR_PADDOCK_RARITY.get(),
                "the loaded structure set must use the configured one-in-N rarity");
    }

    public static void visitorChunkIsSearchable(GameTestHelper helper) {
        var level = helper.getLevel();
        var set = level.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY)
                .get(new ResourceLocation(PortMod.MOD_ID, "visitor_centre"));
        require(helper, set != null && set.placement() instanceof VisitorPlacement placement
                        && placement instanceof RandomSpreadStructurePlacement,
                "visitor placement must be a random-spread placement so structure search does not skip it");
        if (!(set.placement() instanceof VisitorPlacement placement)) return;
        var chunks = level.getChunkSource();
        ChunkGenerator generator = chunks.getGenerator();
        long seed = level.getSeed();
        var candidate = VisitorPlacement.candidateForLocate(level);
        if (candidate.isPresent()) {
            ChunkPos chunk = candidate.get();
            require(helper, placement.getPotentialStructureChunk(seed, 0, 0).equals(chunk)
                            && placement.getPotentialStructureChunk(seed, 400, -20).equals(chunk),
                    "every search cell must name the single visitor chunk");
            require(helper, placement.isPlacementChunk(generator, chunks.randomState(), seed, chunk.x, chunk.z)
                            && !placement.isPlacementChunk(generator, chunks.randomState(), seed, chunk.x + 8, chunk.z),
                    "only the candidate chunk may generate the visitor centre");
        } else {
            require(helper, placement.getPotentialStructureChunk(seed, 3, 4).equals(ChunkPos.ZERO)
                            && !placement.isPlacementChunk(generator, chunks.randomState(), seed, 0, 0),
                    "a world without the biome must not invent a visitor chunk");
        }
    }

    public static void vanillaSearchFindsVisitor(GameTestHelper helper, Holder<Structure> visitor, Optional<ChunkPos> candidate,
            Optional<BlockPos> start) {
        if (start.isEmpty() || candidate.isEmpty()) return;
        var level = helper.getLevel();
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(visitor), BlockPos.ZERO, 2, false);
        require(helper, found != null && found.getSecond().is(VisitorLocate.VISITOR) && new ChunkPos(found.getFirst()).equals(candidate.get()),
                "third-party structure search skipped the visitor centre");
    }

    private static int count(PaddockPlacement placement, long seed, int samples) {
        int hits = 0;
        for (int i = 0; i < samples; i++) if (placement.isPlacementChunk(null, null, seed, i, i * 3 + 1)) hits++;
        return hits;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
