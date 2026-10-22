package ports.jurassicraft.structures;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.mural.MuralEntity;
import ports.jurassicraft.village.VillageProcessor;

/**
 * Places each large structure the way a player meets it: chunk by chunk, then a full
 * {@code /spawnjc} placement, and checks blocks, loot, hanging entities and light.
 * Village notes stay separate. Paddock and visitor-centre mismatches, including fences,
 * walls, vines, mushrooms and levers, fail the test.
 */
public final class StructureWalkAudit {
    private static final BlockPos CONTROLLED = new BlockPos(3208, 90, 3208);
    /** Counts taken from visitor_centre.nbt, not from an earlier guess. */
    private static final Map<String, Integer> VISITOR_BLOCKS = Map.ofEntries(
            Map.entry("jurassicraft:cleaning_station", 2),
            Map.entry("jurassicraft:dna_extractor", 1),
            Map.entry("jurassicraft:dna_sequencer", 1),
            Map.entry("jurassicraft:dna_synthesizer", 1),
            Map.entry("jurassicraft:dna_combinator_hybridizer", 1),
            Map.entry("jurassicraft:embryonic_machine", 2),
            Map.entry("jurassicraft:embryo_calcification_machine", 1),
            Map.entry("jurassicraft:incubator", 2),
            Map.entry("jurassicraft:fossil_grinder", 1),
            Map.entry("minecraft:sea_lantern", 60));

    private StructureWalkAudit() {}

    public static List<String> failures(ServerLevel level) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        split(controlled(level, true, Rotation.NONE, Mirror.NONE), failures, notes);
        split(controlled(level, true, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT), failures, notes);
        split(prefix("paddock", controlled(level, false, Rotation.NONE, Mirror.NONE)), failures, notes);
        split(prefix("paddock", controlled(level, false, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK)), failures, notes);
        notes.addAll(villages(level));
        var natural = natural(level, true);
        if (natural.skipped) notes.add("visitor: " + natural.detail);
        else failures.addAll(natural.issues);
        var paddock = natural(level, false);
        notes.add("paddock natural: " + (paddock.skipped ? paddock.detail : paddock.issues.isEmpty() ? "generated cleanly" : String.join("; ", paddock.issues)));
        write(level, failures, notes);
        return failures;
    }

    private static void split(List<String> issues, List<String> failures, List<String> notes) {
        for (String issue : issues) {
            if (issue.startsWith("note: ")) notes.add(issue.substring(6));
            else failures.add(issue);
        }
    }

    private static List<String> prefix(String name, List<String> issues) {
        List<String> prefixed = new ArrayList<>();
        for (String issue : issues) prefixed.add(name + ": " + issue);
        return prefixed;
    }

    private static void write(ServerLevel level, List<String> failures, List<String> notes) {
        try {
            var path = level.getServer().getServerDirectory().toPath().resolve("structure-walk.txt");
            var lines = new ArrayList<String>();
            lines.add("failures");
            lines.addAll(failures);
            lines.add("notes");
            lines.addAll(notes);
            Files.write(path, lines);
        } catch (IOException ignored) {
            // The GameTest log still carries the assertion text.
        }
    }

    private static List<String> controlled(ServerLevel level, boolean visitor, Rotation rotation, Mirror mirror) {
        String name = visitor ? "visitor_centre" : "raptor_paddock";
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, name);
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        List<String> issues = new ArrayList<>();
        if (template.getSize().getX() <= 0) {
            issues.add(name + " template is missing");
            return issues;
        }
        BlockPos origin = CONTROLLED.offset(visitor ? 0 : 400, visitor ? 0 : 0, mirror == Mirror.NONE ? 0 : 200);
        if (rotation != Rotation.NONE) origin = origin.offset(0, 0, 250);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror)
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        BlockPos referenceOrigin = origin.offset(0, 48, 0);
        BoundingBox referenceBox = template.getBoundingBox(settings, referenceOrigin);
        prepare(level, footprint, true);
        prepare(level, referenceBox, true);
        RandomSource random = RandomSource.create(1234);
        boolean placed = template.placeInWorld(level, referenceOrigin, referenceOrigin, settings, random, ParkEdges.SHAPED);
        if (!placed) issues.add(name + " reference placement failed");
        ParkStructurePiece piece = new ParkStructurePiece(level.getStructureManager(), id, origin, rotation, mirror);
        int minChunkX = footprint.minX() >> 4, maxChunkX = footprint.maxX() >> 4;
        int minChunkZ = footprint.minZ() >> 4, maxChunkZ = footprint.maxZ() >> 4;
        AABB volume = aabb(footprint);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            BoundingBox chunk = new BoundingBox(cx << 4, level.getMinBuildHeight(), cz << 4,
                    (cx << 4) + 15, level.getMaxBuildHeight() - 1, (cz << 4) + 15);
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, chunk,
                    new ChunkPos(cx, cz), origin);
            // A player can load the near side of a structure long enough for hanging entities to give up
            // before the far side of their wall exists.
            for (HangingEntity hanging : level.getEntitiesOfClass(HangingEntity.class, volume, entity -> true)) {
                for (int tick = 0; tick < 101 && !hanging.isRemoved(); tick++) hanging.tick();
            }
        }
        // The chunk loop leaves raw template states. Settle them the way a loaded footprint does.
        ParkEdges.finish(level, footprint);
        issues.addAll(compare(level, name + " " + rotation + "/" + mirror, footprint, referenceBox));
        issues.addAll(contents(level, name, footprint, visitor));
        level.getEntitiesOfClass(Entity.class, volume, entity -> !(entity instanceof Player)).forEach(Entity::discard);
        level.getEntitiesOfClass(Entity.class, aabb(referenceBox), entity -> !(entity instanceof Player)).forEach(Entity::discard);
        return issues;
    }

    private static void prepare(ServerLevel level, BoundingBox box) {
        prepare(level, box, false);
    }

    private static void prepare(ServerLevel level, BoundingBox box, boolean bright) {
        // One block outside the template, including above it, so both footprints see the same
        // neighbours. The copies are stacked in Y and the terrain beside them is not.
        int minX = box.minX() - 1, maxX = box.maxX() + 1, minZ = box.minZ() - 1, maxZ = box.maxZ() + 1;
        int maxY = Math.min(box.maxY() + 1, level.getMaxBuildHeight() - 1);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                level.getChunk(cx, cz);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            chunk.setBlockState(cursor.set(x, box.minY() - 1, z), Blocks.STONE.defaultBlockState(), false);
            for (int y = box.minY(); y <= maxY; y++)
                chunk.setBlockState(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
        }
        AABB area = new AABB(minX, box.minY(), minZ, maxX + 1, maxY + 1, maxZ + 1);
        level.getEntitiesOfClass(Entity.class, area, entity -> !(entity instanceof Player)).forEach(Entity::discard);
        if (bright) PaddockStructureAudit.fullSky(level, new BoundingBox(minX, box.minY(), minZ, maxX, maxY, maxZ));
    }

    private static List<String> compare(ServerLevel level, String label, BoundingBox walked, BoundingBox reference) {
        List<String> issues = new ArrayList<>();
        int missing = 0, different = 0, extra = 0;
        Map<String, Integer> missingNames = new HashMap<>();
        Map<String, Integer> extraNames = new HashMap<>();
        String example = "";
        BlockPos.MutableBlockPos walkedPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos referencePos = new BlockPos.MutableBlockPos();
        int dx = reference.minX() - walked.minX(), dy = reference.minY() - walked.minY(), dz = reference.minZ() - walked.minZ();
        for (int x = walked.minX(); x <= walked.maxX(); x++)
            for (int y = walked.minY(); y <= walked.maxY(); y++)
                for (int z = walked.minZ(); z <= walked.maxZ(); z++) {
                    BlockState walk = level.getBlockState(walkedPos.set(x, y, z));
                    BlockState full = level.getBlockState(referencePos.set(x + dx, y + dy, z + dz));
                    if (walk.equals(full)) continue;
                    if (full.isAir() && !walk.isAir()) {
                        extra++;
                        extraNames.merge(String.valueOf(Registry.BLOCK.getKey(walk.getBlock())), 1, Integer::sum);
                    } else if (!full.isAir() && walk.isAir()) {
                        missing++;
                        missingNames.merge(String.valueOf(Registry.BLOCK.getKey(full.getBlock())), 1, Integer::sum);
                    } else different++;
                    if (example.isEmpty()) example = x + "," + y + "," + z + " chunk-walk " + walk + " full " + full;
                }
        if (missing + different + extra > 0)
            issues.add(label + " chunk walk differs from full placement: missing " + missing + " " + top(missingNames)
                    + ", different " + different + ", extra " + extra + " " + top(extraNames) + " (" + example + ")");
        return issues;
    }

    private static String top(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .toList()
                .toString();
    }

    private static List<String> contents(ServerLevel level, String name, BoundingBox box, boolean visitor) {
        List<String> issues = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        int structureBlocks = 0, loot = 0, laboratoryLoot = 0, dark = 0, lanterns = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) structureBlocks++;
                    String id = String.valueOf(Registry.BLOCK.getKey(state.getBlock()));
                    if (VISITOR_BLOCKS.containsKey(id) || id.equals("minecraft:sea_lantern"))
                        counts.merge(id, 1, Integer::sum);
                    if (state.is(Blocks.SEA_LANTERN)) {
                        lanterns++;
                        if (!lit(level, cursor)) dark++;
                    }
                    var blockEntity = level.getBlockEntity(cursor);
                    if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                        String table = container.saveWithoutMetadata().getString("LootTable");
                        if (!table.isEmpty()) loot++;
                        if (table.endsWith("/laboratory")) laboratoryLoot++;
                    }
                }
        if (structureBlocks > 0) issues.add(name + " left " + structureBlocks + " structure or jigsaw blocks");
        AABB area = aabb(box);
        int murals = level.getEntitiesOfClass(MuralEntity.class, area, entity -> !entity.isRemoved()).size();
        int paintings = level.getEntitiesOfClass(Painting.class, area, entity -> !entity.isRemoved()).size();
        if (visitor) {
            for (var expected : VISITOR_BLOCKS.entrySet()) {
                int found = counts.getOrDefault(expected.getKey(), 0);
                if (found != expected.getValue())
                    issues.add(name + " " + expected.getKey() + " count " + found + ", expected " + expected.getValue());
            }
            if (murals != 11) issues.add(name + " kept " + murals + " murals after staggered chunk loading, expected 11");
            if (paintings != 3) issues.add(name + " kept " + paintings + " paintings after staggered chunk loading, expected 3");
            if (loot < 61) issues.add(name + " assigned loot tables to " + loot + " containers, expected 61");
            if (laboratoryLoot < 6) issues.add(name + " laboratory loot on " + laboratoryLoot + " chests, expected 6");
            if (dark > 0) issues.add(name + " has " + dark + " sea lanterns that do not emit light");
        } else {
            if (loot < 1) issues.add(name + " chest has no loot table");
            if (murals != 0 || paintings != 0) issues.add(name + " spawned unexpected hanging entities");
        }
        if (visitor && lanterns == 0) issues.add(name + " placed no sea lanterns");
        return issues;
    }

    /** Emission is known on this tick. Propagated brightness waits for the light thread. */
    private static boolean lit(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getLightEmission() > 0;
    }

    private record Natural(boolean skipped, String detail, List<String> issues) {}

    private static Natural natural(ServerLevel level, boolean visitor) {
        String name = visitor ? "visitor_centre" : "raptor_paddock";
        Structure structure = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY)
                .get(new ResourceLocation(PortMod.MOD_ID, name));
        if (structure == null) return new Natural(false, "", List.of(name + " structure is not registered"));
        ChunkPos chunk = visitor ? VisitorPlacement.candidate(level).orElse(null) : paddockChunk(level);
        if (chunk == null) return new Natural(true, "no placement chunk in this test world", List.of());
        level.getChunk(chunk.x, chunk.z);
        StructureStart start = level.getChunk(chunk.x, chunk.z).getAllStarts().get(structure);
        if (start == null || !start.isValid() || start.getPieces().isEmpty()
                || !(start.getPieces().get(0) instanceof ParkStructurePiece))
            return new Natural(false, "", List.of(name + " did not generate at " + chunk.x + "," + chunk.z));
        BoundingBox box = start.getBoundingBox();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                level.getChunk(cx, cz);
        ParkEdges.finish(level, box);
        List<String> issues = new ArrayList<>();
        int structureBlocks = 0, nonAir = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!state.isAir()) nonAir++;
                    if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) structureBlocks++;
                }
        if (structureBlocks > 0) issues.add(name + " natural generation left " + structureBlocks + " marker blocks");
        if (nonAir < (visitor ? 10000 : 500)) issues.add(name + " natural generation is nearly empty (" + nonAir + " blocks)");
        AABB area = aabb(box);
        for (HangingEntity hanging : level.getEntitiesOfClass(HangingEntity.class, area, entity -> true))
            for (int tick = 0; tick < 101 && !hanging.isRemoved(); tick++) hanging.tick();
        if (visitor) {
            int murals = level.getEntitiesOfClass(MuralEntity.class, area, entity -> !entity.isRemoved()).size();
            int paintings = level.getEntitiesOfClass(Painting.class, area, entity -> !entity.isRemoved()).size();
            if (murals != 11) issues.add("natural visitor centre kept " + murals + " murals, expected 11");
            if (paintings != 3) issues.add("natural visitor centre kept " + paintings + " paintings, expected 3");
            // Generation aligns the transformed entrance (38, 3, 1) to the ocean floor.
            // The chunk centre can be a peak dozens of blocks away, and each GameTest world
            // has a new seed, so sampling MOTION_BLOCKING there is not the placement height.
            issues.addAll(entranceHeight(level, (ParkStructurePiece) start.getPieces().get(0)));
        }
        return new Natural(false, "", issues);
    }

    private static List<String> entranceHeight(ServerLevel level, ParkStructurePiece piece) {
        BlockPos entrance = StructureTemplate.transform(new BlockPos(38, 3, 1), piece.mirror(), piece.rotation(), BlockPos.ZERO);
        BlockPos origin = piece.origin();
        int columnX = origin.getX() + entrance.getX();
        int columnZ = origin.getZ() + entrance.getZ();
        int floor = level.getChunkSource().getGenerator().getBaseHeight(columnX, columnZ, Heightmap.Types.OCEAN_FLOOR_WG,
                level, level.getChunkSource().randomState());
        int placed = origin.getY() + entrance.getY();
        if (placed != floor)
            return List.of("natural visitor centre entrance is at y=" + placed + ", ocean floor at " + columnX + "," + columnZ + " is " + floor);
        return List.of();
    }

    private static ChunkPos paddockChunk(ServerLevel level) {
        PaddockPlacement placement = new PaddockPlacement();
        var generator = level.getChunkSource().getGenerator();
        var state = level.getChunkSource().randomState();
        TagKey<Biome> tropical = TagKey.create(Registry.BIOME_REGISTRY,
                new ResourceLocation(PortMod.MOD_ID, "has_structure/raptor_paddock"));
        for (int regionX = -12; regionX <= 12; regionX++) for (int regionZ = -12; regionZ <= 12; regionZ++) {
            ChunkPos chunk = placement.getPotentialStructureChunk(level.getSeed(), regionX, regionZ);
            int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
            var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(80), QuartPos.fromBlock(z), state.sampler());
            if (biome.is(tropical)) return chunk;
        }
        return null;
    }

    private static List<String> villages(ServerLevel level) {
        List<String> notes = new ArrayList<>();
        String[] biomes = {"plains", "desert", "savanna", "snowy", "taiga"};
        String[] buildings = {"geneticist_house", "fossildigsite_1", "fossildigsite_2", "fossildigsite_3"};
        int index = 0;
        for (String biome : biomes) for (String variant : new String[]{"", "zombie/"}) for (String building : buildings) {
            String id = "village/" + biome + "/" + variant + building;
            BlockPos origin = CONTROLLED.offset((index % 8) * 24, 140, 500 + (index / 8) * 24);
            index++;
            var settings = new StructurePlaceSettings().setRotation(Rotation.NONE)
                    .addProcessor(new VillageProcessor())
                    .addProcessor(JigsawReplacementProcessor.INSTANCE);
            BoundingBox box = level.getStructureManager().getOrCreate(new ResourceLocation(PortMod.MOD_ID, id))
                    .getBoundingBox(settings, origin);
            prepare(level, box);
            boolean placed = level.getStructureManager().getOrCreate(new ResourceLocation(PortMod.MOD_ID, id))
                    .placeInWorld(level, origin, origin, settings, RandomSource.create(42), 2);
            if (!placed) {
                notes.add(id + " did not place");
                continue;
            }
            int markers = 0, chests = 0, loot = 0, darkTorches = 0, torches = 0;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = box.minX(); x <= box.maxX(); x++)
                for (int y = box.minY(); y <= box.maxY(); y++)
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        BlockState state = level.getBlockState(cursor.set(x, y, z));
                        if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) markers++;
                        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.GLOWSTONE)) {
                            torches++;
                            if (!lit(level, cursor)) darkTorches++;
                        }
                        var blockEntity = level.getBlockEntity(cursor);
                        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                            chests++;
                            if (!container.saveWithoutMetadata().getString("LootTable").isEmpty()) loot++;
                        }
                    }
            if (markers > 0) notes.add(id + " left " + markers + " jigsaw or structure blocks");
            if (chests == 0 || loot != chests) notes.add(id + " loot on " + loot + " of " + chests + " chests");
            if (torches > 0 && darkTorches == torches) notes.add(id + " torches are unlit");
            if (!variant.isEmpty()) continue;
            int villagers = level.getEntitiesOfClass(Villager.class, aabb(box), entity -> true).size();
            if (villagers < 1) notes.add(id + " spawned no villager");
        }
        return notes;
    }

    private static AABB aabb(BoundingBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }
}
