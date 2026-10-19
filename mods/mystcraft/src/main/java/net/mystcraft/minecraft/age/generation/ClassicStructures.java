package net.mystcraft.minecraft.age.generation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Authored native controllers only; source ignores symbol seeds for these four MapGen adapters. */
public final class ClassicStructures {
    private ClassicStructures() {}
    public static Stream<Holder<StructureSet>> select(Stream<Holder<StructureSet>> registered, ClassicGenerationPlan plan) {
        Set<String> authored = plan.alterations().stream().map(ClassicGenerationPlan.Alteration::kind).collect(Collectors.toSet());
        return registered.filter(holder -> {
            String id = holder.unwrapKey().map(key -> key.location().toString()).orElse("");
            return id.equals("minecraft:mineshafts") && authored.contains("mineshafts")
                    || id.equals("minecraft:strongholds") && authored.contains("strongholds")
                    || id.equals("minecraft:villages") && authored.contains("villages")
                    || id.equals("minecraft:nether_complexes") && authored.contains("netherfort");
        }).map(holder -> {
            if (!holder.unwrapKey().map(key -> key.location().toString().equals("minecraft:nether_complexes")).orElse(false)) return holder;
            var fortress = holder.value().structures().stream().filter(entry ->
                    entry.structure().unwrapKey().map(key -> key.location().toString().equals("minecraft:fortress")).orElse(false)).toList();
            if (fortress.isEmpty()) throw new IllegalStateException("Native nether_complexes lacks fortress");
            return Holder.direct(new StructureSet(fortress, holder.value().placement()));
        });
    }

    /** Legacy NetherBridge has no Nether-biome gate; retain authored fortresses in non-Nether Ages. */
    public static void createFortress(ChunkGenerator generator, ClassicGenerationPlan plan, RegistryAccess registries,
                                      RandomState random, StructureManager manager, ChunkAccess chunk,
                                      StructureTemplateManager templates, long worldSeed) {
        if (plan.alterations().stream().noneMatch(a -> a.kind().equals("netherfort")) || !manager.shouldGenerateStructures()) return;
        for (var holder : generator.possibleStructureSets().toList()) {
            StructureSet set = holder.value();
            var entry = set.structures().stream().filter(candidate -> candidate.structure().unwrapKey()
                    .map(key -> key.location().toString().equals("minecraft:fortress")).orElse(false)).findFirst();
            if (entry.isEmpty()) continue;
            var structure = entry.get().structure().value();
            var section = SectionPos.bottomOf(chunk);
            var old = manager.getStartForStructure(section, structure, chunk);
            if (old != null && old.isValid()) continue;
            if (!set.placement().isStructureChunk(generator, random, worldSeed, chunk.getPos().x, chunk.getPos().z)) continue;
            var start = structure.generate(registries, generator, generator.getBiomeSource(), random, templates,
                    worldSeed, chunk.getPos(), old == null ? 0 : old.getReferences(), chunk, biome -> true);
            if (start.isValid()) manager.setStartForStructure(section, structure, start, chunk);
        }
    }

    /** Native locate caches are biome-gated; source-authored fortresses must remain locatable in plains Ages. */
    public static com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos, Holder<net.minecraft.world.level.levelgen.structure.Structure>>
            locateFortress(ChunkGenerator generator, ClassicGenerationPlan plan, net.minecraft.server.level.ServerLevel level,
                           net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> requested,
                           net.minecraft.core.BlockPos center, int radius, boolean skipReferenced) {
        if (plan.alterations().stream().noneMatch(a -> a.kind().equals("netherfort"))
                || !level.structureManager().shouldGenerateStructures()) return null;
        for (var setHolder : generator.possibleStructureSets().toList()) {
            var set = setHolder.value();
            var candidate = set.structures().stream().map(StructureSet.StructureSelectionEntry::structure)
                    .filter(holder -> requested.contains(holder) && holder.unwrapKey()
                            .map(key -> key.location().toString().equals("minecraft:fortress")).orElse(false)).findFirst();
            if (candidate.isEmpty() || !(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement placement)) continue;
            var holder = candidate.get(); var structure = holder.value();
            int step = placement.spacing(), cx = center.getX() >> 4, cz = center.getZ() >> 4;
            for (int ring = 0; ring <= radius; ring++) {
                com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos, Holder<net.minecraft.world.level.levelgen.structure.Structure>> closest = null;
                double distance = Double.POSITIVE_INFINITY;
                for (int x = -ring; x <= ring; x++) for (int z = -ring; z <= ring; z++) {
                    if (Math.abs(x) != ring && Math.abs(z) != ring) continue;
                    var chunkPos = placement.getPotentialStructureChunk(level.getSeed(), cx + step * x, cz + step * z);
                    // StructureCheck's speculative biome test has the same Nether-only gate;
                    // inspect the actual structure-start chunk generated by createFortress instead.
                    {
                        var chunk = level.getChunk(chunkPos.x, chunkPos.z, net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS);
                        var start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
                        if (start == null || !start.isValid() || skipReferenced && !start.canBeReferenced()) continue;
                        if (skipReferenced) {
                            level.structureManager().addReference(start);
                            return com.mojang.datafixers.util.Pair.of(placement.getLocatePos(chunkPos), holder);
                        }
                    }
                    var pos = placement.getLocatePos(chunkPos);
                    double squared = pos.distSqr(center);
                    if (squared < distance) { closest = com.mojang.datafixers.util.Pair.of(pos, holder); distance = squared; }
                }
                if (closest != null) return closest;
            }
        }
        return null;
    }
}
