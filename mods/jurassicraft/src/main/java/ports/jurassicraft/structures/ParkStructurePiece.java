package ports.jurassicraft.structures;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
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
    private final Rotation rotation;
    private final Mirror mirror;
    /** Anchors already spawned. Saved with the piece so a reload cannot place them again. */
    private final Set<BlockPos> placedHanging = new HashSet<>();
    public ParkStructurePiece(StructureTemplateManager manager, ResourceLocation id, BlockPos pos, Rotation rotation, Mirror mirror) {
        super(StructureRegistry.PIECE.get(), 0, manager, id, id.toString(), settings(rotation,mirror), pos);
        this.rotation=rotation; this.mirror=mirror;
    }
    private ParkStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StructureRegistry.PIECE.get(), tag, manager, id -> settings(readRotation(tag), readMirror(tag)));
        rotation=readRotation(tag); mirror=readMirror(tag);
        for (long packed : tag.getLongArray(PLACED_HANGING)) placedHanging.add(BlockPos.of(packed));
    }
    public static ParkStructurePiece load(StructurePieceSerializationContext context, CompoundTag tag) {
        return new ParkStructurePiece(context.structureTemplateManager(),tag);
    }
    private static Rotation readRotation(CompoundTag tag) { return Rotation.valueOf(tag.getString("ParkRotation")); }
    private static Mirror readMirror(CompoundTag tag) { return Mirror.valueOf(tag.getString("ParkMirror")); }
    private static StructurePlaceSettings settings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(mirror)
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putString("ParkRotation",rotation.name()); tag.putString("ParkMirror",mirror.name());
        long[] packed = new long[placedHanging.size()];
        int index = 0;
        for (BlockPos anchor : placedHanging) packed[index++] = anchor.asLong();
        tag.putLongArray(PLACED_HANGING, packed);
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {
        String room = ROOMS.get(marker);
        ResourceLocation loot = "Chest".equals(marker) ? new ResourceLocation("minecraft", "chests/village/village_weaponsmith")
                : room != null ? new ResourceLocation(PortMod.MOD_ID, "structure/visitor_centre/" + room) : null;
        if (loot == null || !bounds.isInside(pos)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        RandomizableContainerBlockEntity.setLootTable(level,random,pos.below(),loot);
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox bounds, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level,structures,generator,random,bounds,chunk,pivot);
        ParkHangingEntities.place(template, templatePosition, placeSettings, bounds, level, placedHanging);
        // Support only this chunk's footprint. Never touch a neighboring chunk during worldgen.
        BoundingBox box = getBoundingBox();
        for (int x=Math.max(box.minX(),bounds.minX());x<=Math.min(box.maxX(),bounds.maxX());x++)
            for (int z=Math.max(box.minZ(),bounds.minZ());z<=Math.min(box.maxZ(),bounds.maxZ());z++) {
                BlockPos base = new BlockPos(x,templatePosition.getY(),z);
                if (level.getBlockState(base).isAir()) continue;
                BlockPos current=base.below();
                while (current.getY() >= level.getMinBuildHeight() && bounds.isInside(current)
                        && (level.getBlockState(current).getMaterial().isReplaceable() || !level.getFluidState(current).isEmpty())) {
                    level.setBlock(current,Blocks.DIRT.defaultBlockState(),2); current=current.below();
                }
            }
    }
}
