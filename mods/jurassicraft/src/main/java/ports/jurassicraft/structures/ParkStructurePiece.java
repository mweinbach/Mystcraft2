package ports.jurassicraft.structures;

import java.util.Map;
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
    private final Rotation rotation;
    private final Mirror mirror;
    public ParkStructurePiece(StructureTemplateManager manager, ResourceLocation id, BlockPos pos, Rotation rotation, Mirror mirror) {
        super(StructureRegistry.PIECE.get(), 0, manager, id, id.toString(), settings(rotation,mirror), pos);
        this.rotation=rotation; this.mirror=mirror;
    }
    private ParkStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StructureRegistry.PIECE.get(), tag, manager, id -> settings(readRotation(tag), readMirror(tag)));
        rotation=readRotation(tag); mirror=readMirror(tag);
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
        // is in the next one. Known shape skips that pass. ParkEdges marks the sensitive blocks for
        // vanilla post-processing and places the finished footprint once every slice is loaded.
        placeSettings.setBoundingBox(bounds);
        boundingBox = template.getBoundingBox(placeSettings, templatePosition);
        if (template.placeInWorld(level, templatePosition, pivot, placeSettings, random, ParkEdges.PLACED)) {
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
        // A complete footprint is shaped here, before hanging entities look at the walls.
        ParkEdges.queue(level, template, templatePosition, placeSettings, bounds);
        ParkHangingEntities.place(template, templatePosition, placeSettings, bounds, level);
    }
}
