package ports.jurassicraft.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

/**
 * Generates plains villages and checks upstream's 0-or-1 building caps.
 * Digsite templates hang below their connector and often lose the street collision check,
 * so the 0-and-1 roll is observed with the geneticist house registered under both categories.
 */
public final class VillageLimitChecks {
    private VillageLimitChecks() {}

    /** Upstream's digsite roll is 50/30/20, expressed as five, three and two pool copies of types 3, 1 and 2. */
    public static void requireUpstreamDigsiteWeights(GameTestHelper helper, List<StructurePoolElement> elements, String prefix) {
        int geneticist = count(elements, prefix + "geneticist_house");
        int digsite1 = count(elements, prefix + "fossildigsite_1");
        int digsite2 = count(elements, prefix + "fossildigsite_2");
        int digsite3 = count(elements, prefix + "fossildigsite_3");
        if (geneticist != 1 || digsite3 != 5 || digsite1 != 3 || digsite2 != 2)
            helper.fail("digsite variants must weigh 5:3:2 for types 3, 1 and 2 at " + prefix
                    + "; got " + digsite3 + ":" + digsite1 + ":" + digsite2 + " geneticist=" + geneticist);
    }

    private static int count(List<StructurePoolElement> elements, String location) {
        int found = 0;
        for (StructurePoolElement element : elements) if (element.toString().contains(location)) found++;
        return found;
    }

    public static void generatedVillageRespectsCaps(GameTestHelper helper) {
        var pools = helper.getLevel().registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        StructureTemplatePool pool = pools.get(new ResourceLocation("minecraft", "village/plains/houses"));
        if (pool == null) {
            helper.fail("missing plains house pool");
            return;
        }
        List<StructurePoolElement> limited = VillagePools.limitedTemplates(pool);
        if (limited.isEmpty()) {
            helper.fail("plains houses were not wrapped with village limits");
            return;
        }
        List<StructurePoolElement> saved = VillagePools.swapTemplates(pool, limited);
        try {
            for (int salt = 1; salt <= 8; salt++) {
                int[] counts = assemble(helper, salt, false);
                if (counts[0] > 1 || counts[1] > 1)
                    helper.fail("village exceeded the upstream 0–1 cap: geneticist=" + counts[0] + " digsite=" + counts[1] + " salt=" + salt);
            }
        } finally {
            VillagePools.swapTemplates(pool, saved);
        }
        var processors = Holder.direct(new StructureProcessorList(List.of(new VillageProcessor())));
        var house = StructurePoolElement.single("jurassicraft:village/plains/geneticist_house", processors)
                .apply(StructureTemplatePool.Projection.RIGID);
        List<StructurePoolElement> both = new ArrayList<>();
        for (int copy = 0; copy < 6; copy++) {
            both.add(new LimitedVillageElement(house, VillageLimits.Category.GENETICIST));
            both.add(new LimitedVillageElement(house, VillageLimits.Category.DIGSITE));
        }
        saved = VillagePools.swapTemplates(pool, both);
        try {
            boolean zeroGeneticist = false, oneGeneticist = false, zeroDigsite = false, oneDigsite = false;
            for (int salt = 11; salt <= 34 && !(zeroGeneticist && oneGeneticist && zeroDigsite && oneDigsite); salt++) {
                int[] counts = assemble(helper, salt, true);
                if (counts[0] > 1 || counts[1] > 1)
                    helper.fail("village exceeded the upstream 0–1 cap: geneticist=" + counts[0] + " digsite=" + counts[1] + " salt=" + salt);
                if (counts[0] == 0) zeroGeneticist = true;
                else oneGeneticist = true;
                if (counts[1] == 0) zeroDigsite = true;
                else oneDigsite = true;
            }
            if (!(zeroGeneticist && oneGeneticist && zeroDigsite && oneDigsite))
                helper.fail("upstream rolls each building category as 0 or 1; saw geneticist " + zeroGeneticist + "/" + oneGeneticist
                        + " digsite " + zeroDigsite + "/" + oneDigsite);
        } finally {
            VillagePools.swapTemplates(pool, saved);
        }
    }

    /** {@code byCategory} counts the test wrappers; otherwise it counts the real template names. */
    private static int[] assemble(GameTestHelper helper, int salt, boolean byCategory) {
        var level = helper.getLevel();
        var chunks = level.getChunkSource();
        var generator = chunks.getGenerator();
        ChunkPos chunk = new ChunkPos(480 + salt * 8, 480);
        var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(), chunks.randomState(),
                level.getStructureManager(), level.getSeed(), chunk, level, biome -> true);
        Structure village = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY)
                .get(new ResourceLocation("minecraft", "village_plains"));
        if (village == null) {
            helper.fail("missing plains village structure");
            return new int[] {0, 0};
        }
        Optional<Structure.GenerationStub> stub = village.findGenerationPoint(context);
        if (stub.isEmpty()) {
            helper.fail("plains village did not assemble at salt " + salt);
            return new int[] {0, 0};
        }
        int geneticist = 0, digsite = 0, pieces = 0;
        for (var piece : stub.get().getPiecesBuilder().build().pieces()) {
            pieces++;
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) continue;
            if (byCategory && poolPiece.getElement() instanceof LimitedVillageElement limited) {
                if (limited.category() == VillageLimits.Category.GENETICIST) geneticist++;
                else digsite++;
                continue;
            }
            String name = poolPiece.getElement().toString();
            if (name.contains("geneticist_house")) geneticist++;
            else if (name.contains("fossildigsite_")) digsite++;
        }
        if (pieces == 0) helper.fail("assembled village had no pieces");
        return new int[] {geneticist, digsite};
    }
}
