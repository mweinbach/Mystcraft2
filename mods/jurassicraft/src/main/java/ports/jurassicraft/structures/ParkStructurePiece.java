package ports.jurassicraft.structures;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import ports.jurassicraft.PortMod;

public final class ParkStructurePiece extends TemplateStructurePiece {
    private static final Map<String, String> ROOMS = Map.of(
            "GroundStorage", "ground_storage", "ControlRoom", "control_room", "Laboratory", "laboratory",
            "Cryonics", "cryonics", "Infirmary", "infirmary", "Garage", "garage", "StaffDorms", "staff_dorms",
            "Kitchen", "kitchen", "DormTower", "dorm_tower", "DiningHall", "dining_hall");
    private static final String PLACED_HANGING = "PlacedHanging";
    /** Chunks whose blocks have been placed. Saved so a reload can see the footprint is complete. */
    private static final String MARKED_EDGES = "MarkedEdges";
    /** The neighbour cascade has run. A later chunk load must not run it again. */
    private static final String EDGES_SETTLED = "EdgesSettled";
    private final Rotation rotation;
    private final Mirror mirror;
    Rotation rotation() { return rotation; }
    Mirror mirror() { return mirror; }
    BlockPos origin() { return templatePosition; }
    /** Anchors already spawned. Saved with the piece so a reload cannot place them again. */
    private final Set<BlockPos> placedHanging = new HashSet<>();
    /** Chunks already placed. Saved with the piece so a reload knows the footprint. */
    private final Set<ChunkPos> markedEdges = new HashSet<>();
    private boolean edgesSettled;
    public ParkStructurePiece(StructureTemplateManager manager, ResourceLocation id, BlockPos pos, Rotation rotation, Mirror mirror) {
        super(StructureRegistry.PIECE.get(), 0, manager, id, id.toString(), settings(rotation,mirror), pos);
        this.rotation=rotation; this.mirror=mirror;
    }
    private ParkStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StructureRegistry.PIECE.get(), tag, manager, id -> settings(readRotation(tag), readMirror(tag)));
        rotation=readRotation(tag); mirror=readMirror(tag);
        for (long packed : tag.getLongArray(PLACED_HANGING)) placedHanging.add(BlockPos.of(packed));
        for (long packed : tag.getLongArray(MARKED_EDGES)) markedEdges.add(new ChunkPos(packed));
        edgesSettled = tag.getBoolean(EDGES_SETTLED);
    }
    public static ParkStructurePiece load(StructurePieceSerializationContext context, CompoundTag tag) {
        return new ParkStructurePiece(context.structureTemplateManager(),tag);
    }
    private static Rotation readRotation(CompoundTag tag) { return Rotation.valueOf(tag.getString("ParkRotation")); }
    private static Mirror readMirror(CompoundTag tag) { return Mirror.valueOf(tag.getString("ParkMirror")); }
    private static StructurePlaceSettings settings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(mirror)
                .setIgnoreEntities(true).setKnownShape(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putString("ParkRotation",rotation.name()); tag.putString("ParkMirror",mirror.name());
        long[] packed = new long[placedHanging.size()];
        int index = 0;
        for (BlockPos anchor : placedHanging) packed[index++] = anchor.asLong();
        tag.putLongArray(PLACED_HANGING, packed);
        long[] chunks = new long[markedEdges.size()];
        index = 0;
        for (ChunkPos marked : markedEdges) chunks[index++] = marked.toLong();
        tag.putLongArray(MARKED_EDGES, chunks);
        tag.putBoolean(EDGES_SETTLED, edgesSettled);
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {
        String room = ROOMS.get(marker);
        ResourceLocation loot = "Chest".equals(marker) ? new ResourceLocation("minecraft", "chests/village/village_weaponsmith")
                : room != null ? new ResourceLocation(PortMod.MOD_ID, "structure/visitor_centre/" + room) : null;
        if (loot == null || !bounds.isInside(pos)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), ParkEdges.PLACED);
        RandomizableContainerBlockEntity.setLootTable(level,random,pos.below(),loot);
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox bounds, ChunkPos chunk, BlockPos pivot) {
        // Flag 2 would connect fences to whatever is already in this slice and pop vines whose support
        // is in the next one. A worldgen slice keeps the template shape and marks fences, walls, vines,
        // mushrooms and levers for vanilla post-processing. A command that already has the whole
        // footprint on a live level shapes it in this pass; those chunks are full, so they are not marked.
        placeSettings.setBoundingBox(bounds);
        boundingBox = template.getBoundingBox(placeSettings, templatePosition);
        boolean worldgen = !(level instanceof ServerLevel);
        boolean whole = ParkEdges.covers(bounds, boundingBox);
        placeSettings.setKnownShape(worldgen || !whole);
        int flags = worldgen || !whole ? ParkEdges.PLACED : Block.UPDATE_CLIENTS;
        if (template.placeInWorld(level, templatePosition, pivot, placeSettings, random, flags)) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(templatePosition, placeSettings, Blocks.STRUCTURE_BLOCK)) {
                if (info.nbt == null || !StructureMode.DATA.name().equals(info.nbt.getString("mode"))) continue;
                handleDataMarker(info.nbt.getString("metadata"), info.pos, level, random, bounds);
            }
        }
        // Support columns stay inside this chunk. The same placement flags keep them from reshaping the floor.
        BoundingBox box = getBoundingBox();
        for (int x=Math.max(box.minX(),bounds.minX());x<=Math.min(box.maxX(),bounds.maxX());x++)
            for (int z=Math.max(box.minZ(),bounds.minZ());z<=Math.min(box.maxZ(),bounds.maxZ());z++) {
                BlockPos base = new BlockPos(x,templatePosition.getY(),z);
                if (level.getBlockState(base).isAir()) continue;
                BlockPos current=base.below();
                while (current.getY() >= level.getMinBuildHeight() && bounds.isInside(current)
                        && (level.getBlockState(current).getMaterial().isReplaceable() || !level.getFluidState(current).isEmpty())) {
                    level.setBlock(current,Blocks.DIRT.defaultBlockState(),ParkEdges.PLACED); current=current.below();
                }
            }
        // Remember the chunk. The cascade waits until every chunk of the footprint is loaded, and
        // then runs once. A chunk that is already full is not marked for vanilla post-processing:
        // that single update does not reproduce the one-shot cascade, and a second one would move
        // a wall off the shape the cascade produced.
        markedEdges.add(chunk);
        ParkHangingEntities.place(template, templatePosition, placeSettings, bounds, level, placedHanging);
    }

    /**
     * Replay edge shapes once the whole footprint is loaded. Chunks a player has inhabited are left
     * alone, and the flag is saved with the piece so a later load does not run this again.
     */
    void settleEdges(ServerLevel level) {
        if (edgesSettled) return;
        BoundingBox box = getBoundingBox();
        int minX = box.minX() >> 4, maxX = box.maxX() >> 4, minZ = box.minZ() >> 4, maxZ = box.maxZ() >> 4;
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            if (level.getChunkSource().getChunkNow(cx, cz) == null) return;
        }
        edgesSettled = true;
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
        ParkEdges.settleFootprint(level, template, templatePosition, settings, box);
    }
}
