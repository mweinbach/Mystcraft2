package ports.jurassicraft.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.mural.MuralEntity;
import ports.jurassicraft.mural.MuralRegistry;
import ports.jurassicraft.mural.MuralType;

/** A saved piece must not spawn a second mural when generation continues after a reload. */
public final class ParkHangingPersistence {
    private ParkHangingPersistence() {}

    public static void reloadedVisitorPieceDoesNotDuplicateMural(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos root = helper.absolutePos(BlockPos.ZERO);
        AABB fixture = new AABB(root, root.offset(32, 36, 32));
        level.getEntitiesOfClass(MuralEntity.class, fixture).forEach(MuralEntity::discard);
        level.getEntitiesOfClass(ItemEntity.class, fixture).forEach(ItemEntity::discard);
        int x = (root.getX() + 15) & ~15, z = (root.getZ() + 15) & ~15;
        BoundingBox bounds = new BoundingBox(x, root.getY(), z, x + 15, root.getY() + 35, z + 15);
        BlockPos marker = new BlockPos(35, 4, 79);
        Rotation rotation = Rotation.CLOCKWISE_90;
        Mirror mirror = Mirror.LEFT_RIGHT;
        BlockPos mappedMarker = StructureTemplate.transform(marker, mirror, rotation, BlockPos.ZERO);
        BlockPos origin = new BlockPos(x + 8 - mappedMarker.getX(), root.getY() + 1, z + 8 - mappedMarker.getZ());
        var piece = new ParkStructurePiece(level.getStructureManager(), new ResourceLocation(PortMod.MOD_ID, "visitor_centre"),
                origin, rotation, mirror);
        var random = RandomSource.create(1234);
        var chunk = new ChunkPos(x >> 4, z >> 4);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, bounds, chunk, origin);
        var context = StructurePieceSerializationContext.fromLevel(level);
        var saved = piece.createTag(context);
        if (saved.getLongArray("PlacedHanging").length == 0)
            helper.fail("placed mural anchors must be saved with the structure piece");
        var reloaded = ParkStructurePiece.load(context, saved);
        reloaded.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, bounds, chunk, origin);
        helper.runAfterDelay(5, () -> {
            var murals = level.getEntitiesOfClass(MuralEntity.class, fixture).stream()
                    .filter(mural -> mural.artwork() == MuralType.TRICERATOPS).toList();
            var drops = level.getEntitiesOfClass(ItemEntity.class, fixture).stream()
                    .filter(item -> item.getItem().is(MuralRegistry.MURAL.get())).toList();
            if (murals.size() != 1 || !drops.isEmpty())
                helper.fail("a reloaded piece must keep the one triceratops mural and drop nothing; murals " + murals.size()
                        + " drops " + drops.size());
            Direction facing = rotation.rotate(mirror.mirror(Direction.NORTH));
            BlockPos anchor = StructureTemplate.transform(new BlockPos(38, 9, 79), mirror, rotation, BlockPos.ZERO)
                    .offset(origin).relative(facing.getCounterClockWise().getOpposite());
            if (!murals.get(0).getPos().equals(anchor) || !murals.get(0).survives())
                helper.fail("the saved mural must still hang at " + anchor);
            murals.forEach(MuralEntity::discard);
            helper.succeed();
        });
    }
}
