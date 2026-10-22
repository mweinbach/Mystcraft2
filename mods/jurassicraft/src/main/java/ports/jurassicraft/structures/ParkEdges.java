package ports.jurassicraft.structures;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A chunk slice cannot see the next slice, and a worldgen region will not write back into it.
 * Blocks stay as the template saved them until every chunk of the footprint is a real chunk,
 * then the whole footprint is placed again the way {@code StructureTemplate.placeInWorld} places
 * a one-shot structure. That second pass is what gives fences, walls, vines, mushrooms and levers
 * the same shape as a single placement. Vanilla post-processing still runs for anything marked
 * before that pass.
 */
public final class ParkEdges {
    /**
     * Place the template state without telling neighbours, so a missing slice cannot pop a vine.
     * Light is left alone too: mushroom survival reads skylight, and an early light queue would
     * darken the interior before the one-shot shape pass runs.
     */
    static final int PLACED = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_LIGHT;
    /** Same neighbour pass as a one-shot, without feeding the light engine until the shapes are done. */
    static final int SHAPED = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_LIGHT;
    private static final ConcurrentHashMap<StructureTemplate, boolean[]> SENSITIVE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Key, Placement> PLACEMENTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ChunkPos, Set<Key>> BY_CHUNK = new ConcurrentHashMap<>();
    /** Chunk-load callbacks must not place blocks; that can load another chunk and deadlock. */
    private static final Set<LevelChunk> LOADED = ConcurrentHashMap.newKeySet();

    private ParkEdges() {}

    /** Remember this slice. A footprint that is already complete on a live level is shaped at once. */
    static void queue(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox bounds) {
        Rotation rotation = settings.getRotation();
        Mirror mirror = settings.getMirror();
        BoundingBox footprint = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation).setMirror(mirror), origin);
        Key key = new Key(template, origin, rotation, mirror);
        PLACEMENTS.computeIfAbsent(key, ignored -> new Placement(template, origin, rotation, mirror, footprint));
        for (int cx = footprint.minX() >> 4; cx <= footprint.maxX() >> 4; cx++)
            for (int cz = footprint.minZ() >> 4; cz <= footprint.maxZ() >> 4; cz++)
                BY_CHUNK.computeIfAbsent(new ChunkPos(cx, cz), ignored -> ConcurrentHashMap.newKeySet()).add(key);
        mark(level, template, origin, settings, bounds);
        if (!(level instanceof ServerLevel server)) return;
        if (!covers(bounds, footprint)) return;
        finish(server, footprint);
    }

    /** Remember a generated chunk. Shaping waits until the server tick, outside the load callback. */
    public static void finish(LevelChunk chunk) {
        if (chunk.getLevel() instanceof ServerLevel) LOADED.add(chunk);
    }

    /** Shapes footprints whose chunks have all loaded. Safe to call from the server tick. */
    public static void tick(MinecraftServer server) {
        if (LOADED.isEmpty()) return;
        Set<LevelChunk> loaded = Set.copyOf(LOADED);
        LOADED.clear();
        for (LevelChunk chunk : loaded) {
            if (!(chunk.getLevel() instanceof ServerLevel level) || level.getServer() != server) continue;
            Set<Key> keys = BY_CHUNK.get(chunk.getPos());
            if (keys == null || keys.isEmpty()) continue;
            for (Key key : Set.copyOf(keys)) {
                Placement placement = PLACEMENTS.get(key);
                if (placement != null && ready(level, placement.footprint)) reshape(level, key, placement);
            }
        }
    }

    /** Tests call this after every slice of a preloaded footprint has been placed. */
    static void finish(ServerLevel level, BoundingBox area) {
        for (Key key : Set.copyOf(PLACEMENTS.keySet())) {
            Placement placement = PLACEMENTS.get(key);
            if (placement == null || !area.intersects(placement.footprint)) continue;
            if (ready(level, placement.footprint)) reshape(level, key, placement);
        }
    }

    private static void mark(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox bounds) {
        boolean[] sensitive = SENSITIVE.computeIfAbsent(template, ParkEdges::readSensitive);
        CompoundTag saved = template.save(new CompoundTag());
        ListTag stored = saved.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size() && i < sensitive.length; i++) {
            if (!sensitive[i]) continue;
            CompoundTag block = stored.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            BlockPos world = StructureTemplate.calculateRelativePosition(settings,
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))).offset(origin);
            if (!bounds.isInside(world)) continue;
            ChunkAccess chunk = level.getChunk(world);
            if (!(chunk instanceof LevelChunk)) chunk.markPosForPostprocessing(world);
        }
    }

    /**
     * Same placement a one-shot {@code placeInWorld} performs. Block entities already given a loot
     * table are put back afterwards; the template pass would otherwise reload their original tag.
     */
    private static void reshape(ServerLevel level, Key key, Placement placement) {
        if (PLACEMENTS.remove(key) == null) return;
        for (int cx = placement.footprint.minX() >> 4; cx <= placement.footprint.maxX() >> 4; cx++)
            for (int cz = placement.footprint.minZ() >> 4; cz <= placement.footprint.maxZ() >> 4; cz++) {
                Set<Key> keys = BY_CHUNK.get(new ChunkPos(cx, cz));
                if (keys != null) keys.remove(key);
            }
        Map<BlockPos, CompoundTag> entities = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BoundingBox box = placement.footprint;
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockEntity entity = level.getBlockEntity(cursor.set(x, y, z));
                    if (entity != null) entities.put(cursor.immutable(), entity.saveWithId());
                }
        // placeInWorld skips a block whose state is already the template state, so the shape pass
        // would never see it. Clearing first makes this the same as placing into empty space.
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                    if (!level.getBlockState(cursor.set(x, y, z)).isAir())
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), PLACED);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(placement.rotation).setMirror(placement.mirror)
                .setIgnoreEntities(true).setKnownShape(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        placement.template.placeInWorld(level, placement.origin, placement.origin, settings, RandomSource.create(1), SHAPED);
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                    level.getLightEngine().checkBlock(cursor.set(x, y, z));
        for (Map.Entry<BlockPos, CompoundTag> entry : entities.entrySet()) {
            BlockState state = level.getBlockState(entry.getKey());
            BlockEntity restored = BlockEntity.loadStatic(entry.getKey(), state, entry.getValue());
            if (restored != null) level.setBlockEntity(restored);
        }
    }

    private static boolean ready(ServerLevel level, BoundingBox footprint) {
        for (int cx = footprint.minX() >> 4; cx <= footprint.maxX() >> 4; cx++)
            for (int cz = footprint.minZ() >> 4; cz <= footprint.maxZ() >> 4; cz++)
                if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk)) return false;
        return true;
    }

    private static boolean covers(BoundingBox bounds, BoundingBox footprint) {
        return bounds.isInside(new BlockPos(footprint.minX(), footprint.minY(), footprint.minZ()))
                && bounds.isInside(new BlockPos(footprint.maxX(), footprint.maxY(), footprint.maxZ()));
    }

    private static boolean[] readSensitive(StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) states[i] = NbtUtils.readBlockState(palette.getCompound(i));
        ListTag stored = saved.getList("blocks", Tag.TAG_COMPOUND);
        boolean[] sensitive = new boolean[stored.size()];
        for (int i = 0; i < stored.size(); i++) {
            Block block = states[stored.getCompound(i).getInt("state")].getBlock();
            sensitive[i] = block instanceof CrossCollisionBlock || block instanceof FenceGateBlock || block instanceof WallBlock
                    || block instanceof VineBlock || block instanceof MushroomBlock || block instanceof HugeMushroomBlock
                    || block instanceof LeverBlock;
        }
        return sensitive;
    }

    private record Key(StructureTemplate template, BlockPos origin, Rotation rotation, Mirror mirror) {}
    private record Placement(StructureTemplate template, BlockPos origin, Rotation rotation, Mirror mirror, BoundingBox footprint) {}
}
