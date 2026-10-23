package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.SchematicEntityManager;
import ports.buildcraft.builders.snapshot.SchematicSafety;
import ports.buildcraft.builders.snapshot.Snapshot;

/**
 * Loader-neutral GameTest bodies for {@link SchematicSafety}: snapshots read from saved or uploaded data are
 * rebuilt from the rules, so a forged blueprint cannot place a block other than the one it is costed as,
 * operator-only or unobtainable blocks, or block entity and entity data no scan records.
 */
public final class SchematicSafetyTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);

    private SchematicSafetyTests() {}

    /** The schematic a scan records for {@code state} placed at {@link #AT}, written as a blueprint palette entry. */
    private static CompoundTag scanned(GameTestHelper helper, BlockState state) {
        helper.setBlock(AT, state);
        BlockPos absolute = helper.absolutePos(AT);
        SchematicBlock schematic = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(helper.getLevel(), absolute, absolute));
        return SchematicBlockManager.writeToNBT(schematic);
    }

    private interface Body {
        void run(GameTestHelper helper) throws InvalidSnapshotException;
    }

    private static void run(GameTestHelper helper, Body body) {
        try {
            body.run(helper);
        } catch (InvalidSnapshotException e) {
            helper.fail("an honest schematic must read back: " + e);
        }
    }

    private static boolean refused(CompoundTag tag) {
        try {
            SchematicBlockManager.readFromNBT(tag);
            return false;
        } catch (InvalidSnapshotException e) {
            return true;
        }
    }

    private static boolean refusedEntity(CompoundTag tag) {
        try {
            SchematicEntityManager.readFromNBT(tag);
            return false;
        } catch (InvalidSnapshotException e) {
            return true;
        }
    }

    private static CompoundTag forged(CompoundTag honest, String factory, BlockState state, String placeBlock) {
        CompoundTag tag = honest.copy();
        tag.putString("name", factory);
        tag.getCompound("data").put("blockState", NbtUtils.writeBlockState(state));
        tag.getCompound("data").putString("placeBlock", placeBlock);
        return tag;
    }

    private static String id(Block block) {
        return Registry.BLOCK.getKey(block).toString();
    }

    /**
     * A palette entry whose placed block differs from the one the rules derive from its (costed) state is
     * refused, as is any entry for bedrock, a command block or another blocked block, a block from a namespace
     * blueprints do not record, or a fluid entry for a block that is not that fluid. Scans record those blocks
     * as air, and honest entries read back unchanged. The built-in list matches {@code buildcraft:schematic_blacklist}.
     */
    public static void forgedBlocksAreRefused(GameTestHelper helper) {
        run(helper, SchematicSafetyTests::forgedBlocksAreRefusedBody);
    }

    private static void forgedBlocksAreRefusedBody(GameTestHelper helper) throws InvalidSnapshotException {
        CompoundTag stone = scanned(helper, Blocks.STONE.defaultBlockState());
        require(helper, SchematicBlockManager.readFromNBT(stone).equals(SchematicBlockManager.readFromNBT(stone.copy()))
                && SchematicBlockManager.writeToNBT(SchematicBlockManager.readFromNBT(stone)).equals(stone), "an honest entry must read back unchanged");
        for (Block placed : List.of(Blocks.BEDROCK, Blocks.COMMAND_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.AIR)) {
            CompoundTag tag = stone.copy();
            tag.getCompound("data").putString("placeBlock", id(placed));
            require(helper, refused(tag), "stone costed but placing " + id(placed) + " must be refused");
        }
        List<Block> blocked = List.of(Blocks.BEDROCK, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
                Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, Blocks.BARRIER, Blocks.LIGHT, Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL,
                Blocks.SPAWNER, Blocks.REINFORCED_DEEPSLATE, Blocks.BUDDING_AMETHYST);
        for (Block block : blocked) {
            require(helper, refused(forged(stone, "buildcraft:default", block.defaultBlockState(), id(block))), id(block) + " must be refused");
            require(helper, SchematicSafety.isForbidden(block.defaultBlockState()), id(block) + " must be forbidden");
            if (block != Blocks.END_PORTAL) {
                require(helper, SchematicBlockManager.readFromNBT(scanned(helper, block.defaultBlockState())).isAir(), "a scan must record " + id(block) + " as air");
            }
        }
        Block buildcraft = BCBuilders.FILLER.get();
        require(helper, refused(forged(stone, "buildcraft:default", buildcraft.defaultBlockState(), id(buildcraft))),
                "blocks of namespaces blueprints do not record must be refused");
        require(helper, refused(forged(stone, "buildcraft:default", Blocks.WATER.defaultBlockState(), id(Blocks.WATER))),
                "a fluid recorded as a plain block (costing nothing) must be refused");

        CompoundTag water = scanned(helper, Blocks.WATER.defaultBlockState());
        require(helper, "buildcraft:fluid".equals(water.getString("name")) && !refused(water), "a water source must be recorded and read as a fluid");
        CompoundTag bedrockFluid = water.copy();
        bedrockFluid.getCompound("data").put("blockState", NbtUtils.writeBlockState(Blocks.BEDROCK.defaultBlockState()));
        require(helper, refused(bedrockFluid), "a fluid entry for bedrock must be refused");
        CompoundTag flowingSource = water.copy();
        flowingSource.getCompound("data").put("blockState", NbtUtils.writeBlockState(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3)));
        require(helper, refused(flowingSource), "a flowing water block saved as a source must be refused");

        Set<Block> tagged = Registry.BLOCK.getTag(SchematicSafety.BLACKLIST).stream().flatMap(set -> set.stream()).map(Holder::value)
                .collect(Collectors.toSet());
        require(helper, tagged.equals(Set.copyOf(SchematicSafety.BUILT_IN_BLACKLIST)),
                "buildcraft:schematic_blacklist must list exactly the built-in blacklist, got " + tagged);
        helper.succeed();
    }

    /**
     * Saved block entity data keeps only what the block's own block entity reads back, under its own id: a
     * chest's loot table, command text and a foreign id are dropped while its items (which it costs) stay; a
     * beehive loses its stored bees; sign text loses click events and selector parts. Building the result
     * places an ordinary chest.
     */
    public static void forgedBlockEntityDataIsSanitized(GameTestHelper helper) {
        run(helper, SchematicSafetyTests::forgedBlockEntityDataIsSanitizedBody);
    }

    private static void forgedBlockEntityDataIsSanitizedBody(GameTestHelper helper) throws InvalidSnapshotException {
        helper.setBlock(AT, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(AT);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        BlockPos absolute = helper.absolutePos(AT);
        SchematicBlock honest = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(helper.getLevel(), absolute, absolute));
        CompoundTag honestTag = SchematicBlockManager.writeToNBT(honest);
        require(helper, SchematicBlockManager.readFromNBT(honestTag).equals(honest), "an honest chest must read back unchanged");

        CompoundTag forgedTag = honestTag.copy();
        CompoundTag tileNbt = forgedTag.getCompound("data").getCompound("tileNbt");
        tileNbt.putString("id", "minecraft:command_block");
        tileNbt.putString("LootTable", "minecraft:chests/end_city_treasure");
        tileNbt.putLong("LootTableSeed", 1);
        tileNbt.putString("Command", "/op @a");
        tileNbt.put("ForgeData", new CompoundTag());
        SchematicBlock read = SchematicBlockManager.readFromNBT(forgedTag);
        CompoundTag clean = read.getPreviewTileNbt();
        require(helper, clean != null && "minecraft:chest".equals(clean.getString("id")) && !clean.contains("LootTable")
                && !clean.contains("LootTableSeed") && !clean.contains("Command") && !clean.contains("ForgeData"),
                "a chest's forged id, loot table, command and loader data must be dropped, got " + clean);
        require(helper, read.equals(honest), "the sanitized chest must equal the honest one");
        require(helper, read.computeRequiredItems().stream().anyMatch(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 3),
                "the kept items must still be costed");
        BlockPos target = new BlockPos(3, 2, 1);
        read.build(helper.getLevel(), helper.absolutePos(target));
        BlockEntity built = helper.getBlockEntity(target);
        require(helper, built instanceof ChestBlockEntity builtChest && builtChest.getItem(0).is(Items.DIAMOND)
                && !built.saveWithoutMetadata().contains("LootTable"), "building the entry must place a plain chest with its items, got " + built);

        CompoundTag hive = scanned(helper, Blocks.BEEHIVE.defaultBlockState());
        CompoundTag bee = new CompoundTag();
        CompoundTag beeData = new CompoundTag();
        beeData.putString("id", "minecraft:wither");
        bee.put("EntityData", beeData);
        ListTag bees = new ListTag();
        bees.add(bee);
        hive.getCompound("data").getCompound("tileNbt").put("Bees", bees);
        CompoundTag hiveNbt = SchematicBlockManager.readFromNBT(hive).getPreviewTileNbt();
        require(helper, hiveNbt != null && hiveNbt.getList("Bees", Tag.TAG_COMPOUND).isEmpty(), "a beehive's stored bees must be dropped, got " + hiveNbt);

        CompoundTag sign = scanned(helper, Blocks.OAK_SIGN.defaultBlockState());
        CompoundTag signNbt = sign.getCompound("data").getCompound("tileNbt");
        signNbt.putString("Text1", "{\"text\":\"hi\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op @a\"}}");
        signNbt.putString("Text2", "{\"selector\":\"@a\"}");
        signNbt.putString("Text3", "{\"text\":\"\",\"extra\":[{\"nbt\":\"Inventory\",\"entity\":\"@p\"}]}");
        CompoundTag cleanSign = SchematicBlockManager.readFromNBT(sign).getPreviewTileNbt();
        String text = cleanSign == null ? "" : cleanSign.toString();
        require(helper, cleanSign != null && cleanSign.getString("Text1").contains("hi") && !text.contains("clickEvent") && !text.contains("run_command")
                && !text.contains("selector") && !text.contains("Inventory"), "sign text must lose click events and selector or data parts, got " + cleanSign);
        helper.succeed();
    }

    /**
     * An entity entry must name an entity a capturing rule records, at a finite place beside its blueprint:
     * a wither is refused, as are an infinite or far-off position; an armour stand loses its riders and a
     * chest minecart its loot table.
     */
    public static void forgedEntitiesAreRefused(GameTestHelper helper) {
        run(helper, SchematicSafetyTests::forgedEntitiesAreRefusedBody);
    }

    private static void forgedEntitiesAreRefusedBody(GameTestHelper helper) throws InvalidSnapshotException {
        CompoundTag armorStand = entity("minecraft:armor_stand", 0.5, 0, 0.5);
        CompoundTag rider = new CompoundTag();
        rider.putString("id", "minecraft:wither");
        ListTag passengers = new ListTag();
        passengers.add(rider);
        armorStand.getCompound("data").getCompound("entityNbt").put("Passengers", passengers);
        CompoundTag readStand = SchematicEntityManager.readFromNBT(armorStand).serializeNBT().getCompound("entityNbt");
        require(helper, "minecraft:armor_stand".equals(readStand.getString("id")) && !readStand.contains("Passengers"),
                "an armour stand's riders must be dropped, got " + readStand);

        CompoundTag minecart = entity("minecraft:chest_minecart", 0.5, 0, 0.5);
        minecart.getCompound("data").getCompound("entityNbt").putString("LootTable", "minecraft:chests/end_city_treasure");
        require(helper, !SchematicEntityManager.readFromNBT(minecart).serializeNBT().getCompound("entityNbt").contains("LootTable"),
                "a chest minecart's loot table must be dropped");

        require(helper, refusedEntity(entity("minecraft:wither", 0.5, 0, 0.5)), "an entity no rule captures must be refused");
        require(helper, refusedEntity(entity("buildcraft:nonexistent", 0.5, 0, 0.5)), "an unknown entity must be refused");
        require(helper, refusedEntity(entity("minecraft:armor_stand", Double.NaN, 0, 0.5)), "an entity at a position that is not finite must be refused");

        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(SchematicBlockManager.readFromNBT(scanned(helper, Blocks.STONE.defaultBlockState())));
        blueprint.data = new int[] {0};
        blueprint.entities.add(SchematicEntityManager.readFromNBT(entity("minecraft:armor_stand", 0.5, 0, 0.5)));
        blueprint.computeKey();
        blueprint.key = new Snapshot.Key(blueprint.key, new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(), "Forged"));
        CompoundTag tag = Snapshot.writeToNBT(blueprint);
        require(helper, Snapshot.readFromNBT(tag.copy()) instanceof Blueprint, "an honest blueprint must read back");
        tag.getList("entities", Tag.TAG_COMPOUND).set(0, entity("minecraft:armor_stand", 1000, 0, 0.5));
        boolean farRefused;
        try {
            Snapshot.readFromNBT(tag);
            farRefused = false;
        } catch (InvalidSnapshotException e) {
            farRefused = true;
        }
        require(helper, farRefused, "an entity far outside its blueprint must be refused");
        helper.succeed();
    }

    /** A {@code buildcraft:default} entity entry for {@code id} at {@code (x, y, z)} relative to its blueprint. */
    static CompoundTag entity(String id, double x, double y, double z) {
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", id);
        CompoundTag data = new CompoundTag();
        data.put("entityNbt", entityNbt);
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        data.put("pos", pos);
        data.put("hangingPos", NbtUtils.writeBlockPos(new BlockPos(Double.isFinite(x) ? (int) Math.floor(x) : 0, (int) Math.floor(y), (int) Math.floor(z))));
        data.putString("hangingFacing", Direction.NORTH.name());
        data.putString("entityRotation", "NONE");
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "buildcraft:default");
        tag.put("data", data);
        return tag;
    }
}
