package ports.jurassicraft.structures;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.tags.TagLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import ports.jurassicraft.PortMod;

/** Checks the park structure tag and that chunked paddock walls match a one-shot placement. */
public final class PaddockStructureAudit {
    private PaddockStructureAudit() {}

    public static List<String> tag(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        ResourceLocation file = new ResourceLocation(PortMod.MOD_ID, "tags/worldgen/structure/park_structures.json");
        var resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            failures.add("missing " + file);
            return failures;
        }
        JsonObject json;
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException failure) {
            failures.add("could not read " + file + ": " + failure.getMessage());
            return failures;
        }
        if (!json.has("values") || !json.get("values").isJsonArray()) {
            failures.add("park structure tag has no values");
            return failures;
        }
        for (JsonElement value : json.getAsJsonArray("values")) {
            if (!value.isJsonObject() || value.getAsJsonObject().get("required") == null
                    || value.getAsJsonObject().get("required").getAsBoolean()
                    || !value.getAsJsonObject().has("id")) {
                failures.add("park structure tag entries must be optional ids, found " + value);
            }
        }
        var registry = server.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        var tag = registry.getTag(TagKey.create(Registry.STRUCTURE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "park_structures")));
        if (tag.isEmpty()) {
            failures.add("park structure tag did not bind");
            return failures;
        }
        for (String name : List.of("visitor_centre", "raptor_paddock")) {
            var key = ResourceKey.create(Registry.STRUCTURE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, name));
            if (!tag.get().contains(registry.getHolderOrThrow(key))) failures.add("bound park tag is missing " + name);
        }
        if (tag.get().size() != 2) failures.add("bound park tag should contain the two park structures, size " + tag.get().size());
        failures.addAll(missingReferences(server));
        return failures;
    }

    /**
     * Forge's create-world screen binds structure tags before datapack structures exist.
     * Optional ids must not log a missing-reference error, and must still produce the tag.
     */
    private static List<String> missingReferences(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        Logger logger = (Logger) LogManager.getLogger(TagLoader.class);
        var appender = new AbstractAppender("jurassicraft-park-tag", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        boolean additive = logger.isAdditive();
        logger.addAppender(appender);
        logger.setAdditive(false);
        Map<ResourceLocation, Collection<String>> loaded;
        try {
            loaded = new TagLoader<String>(id -> Optional.empty(), "tags/worldgen/structure").loadAndBuild(server.getResourceManager());
        } finally {
            logger.removeAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
        }
        List<String> failures = new ArrayList<>();
        ResourceLocation park = new ResourceLocation(PortMod.MOD_ID, "park_structures");
        var bound = loaded.get(park);
        if (bound == null) failures.add("optional park structure ids were dropped when the structures were not registered yet");
        else if (!bound.isEmpty()) failures.add("unregistered park structures were still bound: " + bound);
        for (String message : messages) {
            if (message.contains("jurassicraft:park_structures") || message.contains("jurassicraft:visitor_centre")
                    || message.contains("jurassicraft:raptor_paddock")) {
                failures.add("early structure bind logged " + message);
            }
        }
        return failures;
    }

    public static List<String> walls(ServerLevel level) {
        List<String> failures = new ArrayList<>();
        // Local (7, 3, 10) is the reported paddock wall, and it lands on a chunk boundary.
        // Footprints sit above the terrain so both placements read the same skylight. The one-shot
        // copy uses its own footprint so the first placement's light is not reused.
        BlockPos edge = new BlockPos(3608, 240, 3208);
        failures.addAll(compare(level, "raptor_paddock", edge, edge.offset(0, 0, 120), Rotation.NONE, Mirror.NONE));
        failures.addAll(compare(level, "raptor_paddock", edge.offset(0, 0, 400), edge.offset(0, 0, 520), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK));
        BlockPos visitor = new BlockPos(3208, 240, 6000);
        failures.addAll(compare(level, "visitor_centre", visitor, visitor.offset(0, 0, 300), Rotation.NONE, Mirror.NONE));
        failures.addAll(compare(level, "visitor_centre", visitor.offset(0, 0, 900), visitor.offset(0, 0, 1200), Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT));
        return failures;
    }

    /**
     * Chunk slices are shaped together once the footprint is loaded, then compared with a one-shot
     * {@code placeInWorld} on a second footprint. Fences, walls, vines, mushrooms and levers must match.
     */
    private static List<String> compare(ServerLevel level, String name, BlockPos origin, BlockPos reference, Rotation rotation, Mirror mirror) {
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, name);
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        if (template.getSize().getX() <= 0) return List.of(name + " template is missing");
        var settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror)
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        BoundingBox referenceBox = template.getBoundingBox(settings, reference);
        Map<Long, BlockState> walked = placeChunked(level, id, template, origin, rotation, mirror, footprint);
        prepare(level, referenceBox);
        boolean placed = template.placeInWorld(level, reference, reference, settings, RandomSource.create(1234), ParkEdges.SHAPED);
        if (!placed) return List.of(name + " one-shot placement failed");
        int walls = 0;
        int edges = 0;
        int mismatches = 0;
        List<String> examples = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = referenceBox.minX(); x <= referenceBox.maxX(); x++)
            for (int y = referenceBox.minY(); y <= referenceBox.maxY(); y++)
                for (int z = referenceBox.minZ(); z <= referenceBox.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState full = level.getBlockState(cursor);
                    if (full.getBlock() instanceof WallBlock) walls++;
                    int dx = x - reference.getX(), dy = y - reference.getY(), dz = z - reference.getZ();
                    long key = BlockPos.asLong(dx, dy, dz);
                    BlockState chunk = walked.getOrDefault(key, Blocks.AIR.defaultBlockState());
                    if (!sensitive(chunk) && !sensitive(full)) continue;
                    edges++;
                    if (chunk.equals(full)) continue;
                    mismatches++;
                    if (examples.size() < 8) {
                        examples.add(new BlockPos(dx, dy, dz) + " chunk " + chunk + " one-shot " + full);
                    }
                }
        List<String> failures = new ArrayList<>();
        if (mismatches > 0) {
            failures.add(name + " " + rotation + "/" + mirror + " chunk edges differ in " + mismatches
                    + " fences, walls, vines, mushrooms or levers: " + examples);
        }
        if (name.equals("raptor_paddock") && walls != 15) failures.add("expected 15 paddock walls, found " + walls);
        if (edges == 0) failures.add(name + " has no edge-sensitive blocks");
        if (name.equals("raptor_paddock") && rotation == Rotation.NONE) {
            BlockState reported = level.getBlockState(reference.offset(7, 3, 10));
            BlockState walkedWall = walked.getOrDefault(BlockPos.asLong(7, 3, 10), Blocks.AIR.defaultBlockState());
            if (!reported.is(Blocks.MOSSY_COBBLESTONE_WALL) || !reported.equals(walkedWall)) {
                failures.add("reported paddock wall at local (7, 3, 10) chunk " + walkedWall + " one-shot " + reported);
            }
        }
        return failures;
    }

    private static boolean sensitive(BlockState state) {
        var block = state.getBlock();
        return block instanceof WallBlock || block instanceof CrossCollisionBlock || block instanceof FenceGateBlock
                || block instanceof VineBlock || block instanceof MushroomBlock || block instanceof HugeMushroomBlock
                || block instanceof LeverBlock;
    }

    private static Map<Long, BlockState> placeChunked(ServerLevel level, ResourceLocation id, StructureTemplate template,
            BlockPos origin, Rotation rotation, Mirror mirror, BoundingBox footprint) {
        prepare(level, footprint);
        var piece = new ParkStructurePiece(level.getStructureManager(), id, origin, rotation, mirror);
        RandomSource random = RandomSource.create(1234);
        int minChunkX = footprint.minX() >> 4, maxChunkX = footprint.maxX() >> 4;
        int minChunkZ = footprint.minZ() >> 4, maxChunkZ = footprint.maxZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            BoundingBox chunk = new BoundingBox(cx << 4, level.getMinBuildHeight(), cz << 4,
                    (cx << 4) + 15, level.getMaxBuildHeight() - 1, (cz << 4) + 15);
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, chunk,
                    new ChunkPos(cx, cz), origin);
        }
        // Preloaded test chunks never fire the worldgen load hook.
        ParkEdges.finish(level, footprint);
        Map<Long, BlockState> states = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++)
            for (int y = footprint.minY(); y <= footprint.maxY(); y++)
                for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (sensitive(state)) states.put(BlockPos.asLong(x - origin.getX(), y - origin.getY(), z - origin.getZ()), state);
                }
        return states;
    }

    private static void prepare(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = box.minX() - 1, maxX = box.maxX() + 1, minZ = box.minZ() - 1, maxZ = box.maxZ() + 1;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) level.getChunk(cx, cz);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            chunk.setBlockState(cursor.set(x, box.minY() - 1, z), Blocks.STONE.defaultBlockState(), false);
            for (int y = box.minY(); y <= box.maxY(); y++)
                chunk.setBlockState(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
        }
        // Clearing blocks does not relight, and the game-test light engine cannot be pumped here.
        // Both footprints get full skylight so mushrooms and the vines beside them settle alike.
        fullSky(level, new BoundingBox(minX, box.minY(), minZ, maxX, box.maxY(), maxZ));
    }

    static void fullSky(ServerLevel level, BoundingBox box) {
        DataLayerStorageMap visible;
        DataLayerStorageMap updating;
        try {
            Field skyField = LevelLightEngine.class.getDeclaredField("skyEngine");
            skyField.setAccessible(true);
            Object sky = skyField.get(level.getLightEngine());
            if (sky == null) return;
            Field storageField = LayerLightEngine.class.getDeclaredField("storage");
            storageField.setAccessible(true);
            Object storage = storageField.get(sky);
            Field visibleField = LayerLightSectionStorage.class.getDeclaredField("visibleSectionData");
            Field updatingField = LayerLightSectionStorage.class.getDeclaredField("updatingSectionData");
            visibleField.setAccessible(true);
            updatingField.setAccessible(true);
            visible = (DataLayerStorageMap) visibleField.get(storage);
            updating = (DataLayerStorageMap) updatingField.get(storage);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                for (int sy = box.minY() >> 4; sy <= box.maxY() >> 4; sy++) {
                    long section = SectionPos.asLong(cx, sy, cz);
                    // A missing layer is read from the section above, which can still be buried terrain.
                    // These footprints sit at y=240, after the chunk has finished lighting.
                    visible.setLayer(section, fullSection());
                    updating.setLayer(section, fullSection());
                }
        visible.clearCache();
        updating.clearCache();
    }

    private static DataLayer fullSection() {
        DataLayer layer = new DataLayer();
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    layer.set(x, y, z, 15);
        return layer;
    }
}
