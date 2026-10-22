package ports.jurassicraft.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.concurrent.atomic.AtomicInteger;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.mural.MuralRegistry;
import ports.jurassicraft.mural.MuralEntity;
import ports.jurassicraft.mural.MuralType;

/** Real template-piece placement, clipped to a chunk inside each test's own fixture. */
public final class ParkStructureGameTests implements FabricGameTest {
    public ParkStructureGameTests() {}

    private static void check(GameTestHelper helper, boolean value, String message) {
        if (!value) helper.fail(message);
    }

    private record Placement(BlockPos origin, BoundingBox bounds, BlockPos outside, Rotation rotation, Mirror mirror) {
        BlockPos position(BlockPos local) {
            return StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO).offset(origin);
        }
    }

    private static Placement placeChunk(GameTestHelper helper, String name, BlockPos marker, Rotation rotation, Mirror mirror) {
        var level = helper.getLevel();
        BlockPos root = helper.absolutePos(BlockPos.ZERO);
        // The fixture is 32 blocks wide, so it always contains a complete aligned chunk.
        int x = (root.getX() + 15) & ~15, z = (root.getZ() + 15) & ~15;
        BoundingBox bounds = new BoundingBox(x, root.getY(), z, x + 15, root.getY() + 35, z + 15);
        BlockPos mappedMarker = StructureTemplate.transform(marker, mirror, rotation, BlockPos.ZERO);
        BlockPos origin = new BlockPos(x + 8 - mappedMarker.getX(), root.getY() + 1, z + 8 - mappedMarker.getZ());
        // This sentinel remains inside the fixture but outside the placed chunk.
        int outsideX = x > root.getX() ? x - 1 : x + 16;
        BlockPos outside = new BlockPos(outsideX, root.getY() + 4, z + 8);
        level.setBlock(outside, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        var piece = new ParkStructurePiece(level.getStructureManager(), new ResourceLocation(PortMod.MOD_ID, name), origin, rotation, mirror);
        check(helper, piece.placeSettings().isIgnoreEntities(), "entities must use clipped anchor placement");
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(1234), bounds,
                new ChunkPos(x >> 4, z >> 4), origin);
        check(helper, level.getBlockState(outside).is(Blocks.GOLD_BLOCK), "placement must not write outside its chunk");
        return new Placement(origin, bounds, outside, rotation, mirror);
    }

    private static void checkLoot(GameTestHelper helper, Placement placement, BlockPos marker, String lootTable) {
        var level = helper.getLevel();
        BlockPos worldMarker = placement.position(marker);
        check(helper, level.getBlockState(worldMarker).isAir(), "data marker must be removed");
        var blockEntity = level.getBlockEntity(worldMarker.below());
        check(helper, blockEntity instanceof ChestBlockEntity, "data marker must have a chest beneath it");
        check(helper, lootTable.equals(blockEntity.saveWithoutMetadata().getString("LootTable")), "chest must receive the marker's loot table");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void paddockRarityMatchesUpstreamChunkRoll(GameTestHelper helper) {
        StructureParityChecks.paddockRollsPerChunk(helper);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void paddockBiomeProbePreservesVerticalBiomesAndSlope(GameTestHelper helper) {
        BoundingBox box = new BoundingBox(0, 0, 0, 3, 0, 3);
        AtomicInteger samples = new AtomicInteger();
        var absent = ParkStructure.paddockHeight(box, (x, z) -> { samples.incrementAndGet(); return 64; }, q -> false);
        check(helper, absent.isEmpty() && samples.get() == 1,
                "an ineligible biome column must stop after the first surface sample");

        samples.set(0);
        // The first surface is 64, but an eligible perimeter may descend to 48.
        // Its floor is 47, in quart 11: do not reject a biome found only there.
        var low = ParkStructure.paddockHeight(box, (x, z) -> {
            samples.incrementAndGet(); return x == 0 && z == 0 ? 64 : 48;
        }, q -> q == 11);
        check(helper, low.isPresent() && low.getAsInt() == 47 && samples.get() == 12,
                "an eligible lower vertical biome must preserve the complete perimeter and original floor");

        samples.set(0);
        var steep = ParkStructure.paddockHeight(box, (x, z) -> {
            samples.incrementAndGet(); return x == 0 && z == 0 ? 64 : 81;
        }, q -> true);
        check(helper, steep.isEmpty() && samples.get() == 12,
                "an eligible biome must retain complete slope sampling and reject height ranges over 16");

        var belowZero = ParkStructure.paddockHeight(box, (x, z) -> x == 0 && z == 0 ? -60 : -76, q -> q == -20);
        check(helper, belowZero.isPresent() && belowZero.getAsInt() == -77,
                "negative biome quart coordinates must use floor division");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:park_structure_test")
    public void paddockChunkPlacesDoorAndLoot(GameTestHelper helper) {
        BlockPos marker = new BlockPos(27, 5, 10);
        Rotation rotation = Rotation.CLOCKWISE_90;
        Mirror mirror = Mirror.FRONT_BACK;
        Placement placed = placeChunk(helper, "raptor_paddock", marker, rotation, mirror);
        checkLoot(helper, placed, marker, "minecraft:chests/village/village_weaponsmith");
        BlockPos door = placed.position(new BlockPos(26, 5, 9));
        var lower = helper.getLevel().getBlockState(door);
        var upper = helper.getLevel().getBlockState(door.above());
        check(helper, new ResourceLocation(PortMod.MOD_ID, "calamites_door").equals(Registry.BLOCK.getKey(lower.getBlock()))
                && lower.getBlock() == upper.getBlock(), "both halves of the original paddock door must be placed");
        check(helper, lower.getValue(DoorBlock.FACING) == rotation.rotate(mirror.mirror(Direction.SOUTH)), "door must follow the structure transform");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:park_structure_test")
    public void visitorChunkPlacesLootAndAnchoredMural(GameTestHelper helper) {
        BlockPos root = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().getEntitiesOfClass(MuralEntity.class, new AABB(root, root.offset(32, 36, 32))).forEach(MuralEntity::discard);
        BlockPos marker = new BlockPos(35, 4, 79);
        Rotation rotation = Rotation.CLOCKWISE_90;
        Mirror mirror = Mirror.LEFT_RIGHT;
        Placement placed = placeChunk(helper, "visitor_centre", marker, rotation, mirror);
        checkLoot(helper, placed, marker, "jurassicraft:structure/visitor_centre/ground_storage");
        Direction facing = rotation.rotate(mirror.mirror(Direction.NORTH));
        BlockPos anchor = placed.position(new BlockPos(38, 9, 79)).relative(facing.getCounterClockWise().getOpposite());
        helper.runAfterDelay(2, () -> {
            var nearby = helper.getLevel().getEntities(MuralRegistry.ENTITY.get(), new AABB(root, root.offset(32, 36, 32)), entity -> true);
            var murals = nearby.stream()
                    .filter(mural -> mural.artwork() == MuralType.TRICERATOPS).toList();
            check(helper, murals.size() == 1, "the original triceratops mural must be spawned exactly once; nearby: "
                    + nearby.stream().map(mural -> mural.artwork().id() + "@" + mural.getPos()).toList() + ", expected: " + anchor);
            var mural = murals.get(0);
            check(helper, mural.getPos().equals(anchor), "mural must retain its transformed anchor after entity placement");
            check(helper, mural.getDirection() == facing, "mural must face the transformed direction");
            check(helper, mural.survives(), "mural must remain attached to the placed wall");
            nearby.forEach(MuralEntity::discard);
            helper.succeed();
        });
    }
    @GameTest(template = "jurassicraft:park_structure_test")
    public void visitorPaintingKeepsTransformedAnchor(GameTestHelper helper) {
        BlockPos root = helper.absolutePos(BlockPos.ZERO);
        AABB fixture = new AABB(root, root.offset(32, 36, 32));
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, fixture).forEach(net.minecraft.world.entity.decoration.Painting::discard);
        Rotation rotation = Rotation.COUNTERCLOCKWISE_90;
        Mirror mirror = Mirror.FRONT_BACK;
        BlockPos target = new BlockPos(66, 4, 70);
        Placement placed = placeChunk(helper, "visitor_centre", target, rotation, mirror);
        BlockPos anchor = placed.position(target);
        Direction expected = rotation.rotate(mirror.mirror(Direction.NORTH));
        helper.runAfterDelay(2, () -> {
            var matching = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, fixture).stream()
                    .filter(painting -> painting.getPos().equals(anchor)).toList();
            check(helper, matching.size() == 1 && matching.get(0).getDirection() == expected,
                    "the painting must appear exactly once at its transformed anchor and facing");
            helper.succeed();
        });
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void datapackEntitiesRemainSupportedAndChunkClipped(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        var template = new StructureTemplate();
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("NoGravity", true);
        tag.putString("CustomName", "{\"text\":\"Park fixture\"}");
        var local = new net.minecraft.world.phys.Vec3(1.5, 0, 1.5);
        template.entityInfoList
                .add(new StructureTemplate.StructureEntityInfo(local, new BlockPos(1, 0, 1), tag));
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setMirror(Mirror.LEFT_RIGHT).setRotation(Rotation.CLOCKWISE_90);
        var transformed = StructureTemplate.transform(local, Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_90, BlockPos.ZERO)
                .add(origin.getX(), origin.getY(), origin.getZ());
        BlockPos anchor = new BlockPos(transformed);
        var bounds = new BoundingBox(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX(), anchor.getY(), anchor.getZ());
        ParkHangingEntities.place(template, origin, settings, bounds, level);
        ParkHangingEntities.place(template, origin, settings, new BoundingBox(anchor.offset(4, 0, 0)), level);
        helper.runAfterDelay(2, () -> {
            var matches = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    new AABB(anchor).inflate(2), entity -> entity.hasCustomName() && entity.getName().getString().equals("Park fixture"));
            check(helper, matches.size() == 1, "non-hanging datapack entities must spawn exactly once in their anchor chunk");
            matches.forEach(entity -> entity.discard());
            helper.succeed();
        });
    }
}
