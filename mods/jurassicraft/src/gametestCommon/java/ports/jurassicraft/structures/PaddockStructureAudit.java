package ports.jurassicraft.structures;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
        // Local (7, 3, 10) lands on a chunk edge, which is where the tall/low mismatch showed up.
        BlockPos edge = new BlockPos(3608, 80, 3208);
        failures.addAll(compare(level, edge, edge.offset(0, 0, 400), Rotation.NONE, Mirror.NONE));
        failures.addAll(compare(level, edge.offset(0, 0, 800), edge.offset(0, 0, 1200), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK));
        failures.addAll(expect(level, edge.offset(0, 0, 400), new BlockPos(7, 3, 10), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(),
                WallSide.LOW, WallSide.LOW, WallSide.NONE, WallSide.NONE, true));
        failures.addAll(expect(level, edge.offset(0, 0, 400), new BlockPos(7, 7, 10), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(),
                WallSide.LOW, WallSide.LOW, WallSide.NONE, WallSide.NONE, false));
        failures.addAll(expect(level, edge.offset(0, 0, 400), new BlockPos(7, 7, 11), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(),
                WallSide.LOW, WallSide.LOW, WallSide.NONE, WallSide.NONE, false));
        return failures;
    }

    private static List<String> compare(ServerLevel level, BlockPos walkedOrigin, BlockPos fullOrigin, Rotation rotation, Mirror mirror) {
        List<String> failures = new ArrayList<>();
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, "raptor_paddock");
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        if (template.getSize().getX() <= 0) return List.of("raptor paddock template is missing");
        var settings = new StructurePlaceSettings()
                .setRotation(rotation).setMirror(mirror);
        place(level, id, template, walkedOrigin, rotation, mirror, true);
        place(level, id, template, fullOrigin, rotation, mirror, false);
        int walls = 0;
        for (var entry : ParkWalls.blocks(template).entrySet()) {
            if (!(entry.getValue().getBlock() instanceof WallBlock)) continue;
            walls++;
            BlockPos local = StructureTemplate.calculateRelativePosition(settings, entry.getKey());
            BlockState walked = level.getBlockState(walkedOrigin.offset(local));
            BlockState full = level.getBlockState(fullOrigin.offset(local));
            if (!walked.equals(full)) {
                failures.add(rotation + "/" + mirror + " wall " + entry.getKey() + " chunk " + walked + " one-shot " + full);
            }
        }
        if (walls != 15) failures.add("expected 15 paddock walls, found " + walls);
        return failures;
    }

    private static List<String> expect(ServerLevel level, BlockPos origin, BlockPos local, BlockState block,
            WallSide north, WallSide south, WallSide west, WallSide east, boolean post) {
        BlockState state = level.getBlockState(origin.offset(local));
        boolean matches = state.is(block.getBlock())
                && state.getValue(WallBlock.NORTH_WALL) == north && state.getValue(WallBlock.SOUTH_WALL) == south
                && state.getValue(WallBlock.WEST_WALL) == west && state.getValue(WallBlock.EAST_WALL) == east
                && state.getValue(WallBlock.UP) == post;
        if (matches) return List.of();
        return List.of(local + " should be a 1.12 " + block.getBlock() + " north " + north + " south " + south
                + " west " + west + " east " + east + " post " + post + ", found " + state);
    }

    private static void place(ServerLevel level, ResourceLocation id, StructureTemplate template, BlockPos origin,
            Rotation rotation, Mirror mirror, boolean chunkByChunk) {
        var settings = new StructurePlaceSettings()
                .setRotation(rotation).setMirror(mirror);
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        clear(level, footprint);
        var piece = new ParkStructurePiece(level.getStructureManager(), id, origin, rotation, mirror);
        RandomSource random = RandomSource.create(1234);
        if (!chunkByChunk) {
            BoundingBox all = new BoundingBox(footprint.minX(), level.getMinBuildHeight(), footprint.minZ(),
                    footprint.maxX(), level.getMaxBuildHeight() - 1, footprint.maxZ());
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, all,
                    new ChunkPos(origin), origin);
            return;
        }
        int minChunkX = footprint.minX() >> 4, maxChunkX = footprint.maxX() >> 4;
        int minChunkZ = footprint.minZ() >> 4, maxChunkZ = footprint.maxZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            BoundingBox chunk = new BoundingBox(cx << 4, level.getMinBuildHeight(), cz << 4,
                    (cx << 4) + 15, level.getMaxBuildHeight() - 1, (cz << 4) + 15);
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, chunk,
                    new ChunkPos(cx, cz), origin);
        }
    }

    private static void clear(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) level.getChunk(cx, cz);
        for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            for (int y = box.minY(); y <= box.maxY(); y++)
                chunk.setBlockState(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
        }
    }
}
