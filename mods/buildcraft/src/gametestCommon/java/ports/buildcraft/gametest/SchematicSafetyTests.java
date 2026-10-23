package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import net.minecraft.world.entity.player.Player;
import ports.buildcraft.builders.ArchitectTableBlock;
import ports.buildcraft.builders.ArchitectTableBlockEntity;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.FillerBlockEntity;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.SchematicEntity;
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

        helper.setBlock(AT, Blocks.FURNACE);
        ((net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(AT)).setItem(0, new ItemStack(Items.IRON_ORE, 2));
        CompoundTag furnace = SchematicBlockManager.writeToNBT(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(helper.getLevel(), absolute, absolute)));
        CompoundTag furnaceTile = furnace.getCompound("data").getCompound("tileNbt");
        CompoundTag recipesUsed = new CompoundTag();
        recipesUsed.putInt("minecraft:iron_ingot_from_smelting_iron_ore", 1_000_000);
        furnaceTile.put("RecipesUsed", recipesUsed);
        furnaceTile.putShort("BurnTime", (short) 20000);
        furnaceTile.putShort("CookTime", (short) 199);
        furnaceTile.putShort("CookTimeTotal", (short) 200);
        SchematicBlock furnaceRead = SchematicBlockManager.readFromNBT(furnace);
        CompoundTag furnaceNbt = furnaceRead.getPreviewTileNbt();
        require(helper, furnaceNbt != null && !furnaceNbt.contains("RecipesUsed") && !furnaceNbt.contains("BurnTime") && !furnaceNbt.contains("CookTime")
                && !furnaceNbt.contains("CookTimeTotal") && furnaceNbt.contains("Items"),
                "a furnace's stored experience and operating state must be dropped while its costed items stay, got " + furnaceNbt);
        require(helper, furnaceRead.computeRequiredItems().stream().anyMatch(stack -> stack.is(Items.IRON_ORE) && stack.getCount() == 2),
                "the furnace's kept items must still be costed");

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

    /** {@code depth} nested JSON arrays around one string: valid text component JSON that recurses that deep. */
    private static String nested(int depth) {
        return "[".repeat(depth) + "\"x\"" + "]".repeat(depth);
    }

    private static SchematicEntity readInLevel(GameTestHelper helper, CompoundTag tag) throws InvalidSnapshotException {
        try {
            return SchematicSafety.readInLevel(helper.getLevel(), () -> SchematicEntityManager.readFromNBT(tag));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Data that would cost the server to read is bounded. Sign text and an armour stand's name nested thousands
     * deep are emptied before anything parses them (parsing them would overflow the stack). An armour stand read
     * in a level, as uploads are, keeps only what an armour stand reads back, so arbitrary extra data is dropped.
     * A block entity or entity still carrying more than {@link SchematicSafety#MAX_SCHEMATIC_NBT_BYTES} is refused.
     */
    public static void schematicDataIsBounded(GameTestHelper helper) {
        run(helper, SchematicSafetyTests::schematicDataIsBoundedBody);
    }

    private static void schematicDataIsBoundedBody(GameTestHelper helper) throws InvalidSnapshotException {
        String deep = nested(5000);
        require(helper, !SchematicSafety.isBoundedComponent(deep) && SchematicSafety.isBoundedComponent("{\"text\":\"hi\"}"),
                "deeply nested text must count as unbounded and plain text as bounded");
        CompoundTag sign = scanned(helper, Blocks.OAK_SIGN.defaultBlockState());
        sign.getCompound("data").getCompound("tileNbt").putString("Text1", deep);
        CompoundTag cleanSign = SchematicBlockManager.readFromNBT(sign).getPreviewTileNbt();
        require(helper, cleanSign != null && cleanSign.getString("Text1").length() < 64, "deeply nested sign text must be emptied, got "
                + (cleanSign == null ? null : cleanSign.getString("Text1").length() + " characters"));

        CompoundTag stand = entity("minecraft:armor_stand", 0.5, 0, 0.5);
        CompoundTag standNbt = stand.getCompound("data").getCompound("entityNbt");
        standNbt.putString("CustomName", deep);
        standNbt.putByteArray("padding", new byte[4096]);
        CompoundTag read = SchematicEntityManager.readFromNBT(stand.copy()).serializeNBT().getCompound("entityNbt");
        require(helper, read.getString("CustomName").length() < 64, "a deeply nested entity name must be emptied, got "
                + read.getString("CustomName").length() + " characters");
        CompoundTag rebuilt = readInLevel(helper, stand.copy()).serializeNBT().getCompound("entityNbt");
        require(helper, "minecraft:armor_stand".equals(rebuilt.getString("id")) && !rebuilt.contains("padding")
                && rebuilt.contains("ShowArms") && rebuilt.getString("CustomName").length() < 64,
                "an armour stand read in a level must keep only what an armour stand saves, got " + rebuilt.getAllKeys());

        CompoundTag huge = entity("minecraft:armor_stand", 0.5, 0, 0.5);
        huge.getCompound("data").getCompound("entityNbt").putByteArray("padding", new byte[SchematicSafety.MAX_SCHEMATIC_NBT_BYTES + 1]);
        require(helper, refusedEntity(huge.copy()), "an entity carrying more than the limit must be refused");
        require(helper, !readInLevel(helper, huge).serializeNBT().getCompound("entityNbt").contains("padding"),
                "read in a level, the oversized padding is dropped with the rest of what an armour stand does not read");

        helper.setBlock(AT, Blocks.CHEST);
        ItemStack padded = new ItemStack(Items.STONE);
        padded.getOrCreateTag().putByteArray("padding", new byte[SchematicSafety.MAX_SCHEMATIC_NBT_BYTES + 1]);
        ((ChestBlockEntity) helper.getBlockEntity(AT)).setItem(0, padded);
        BlockPos absolute = helper.absolutePos(AT);
        CompoundTag chest = SchematicBlockManager.writeToNBT(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(helper.getLevel(), absolute, absolute)));
        require(helper, refused(chest), "a block entity carrying more than the limit must be refused");
        helper.setBlock(AT, Blocks.AIR);
        helper.succeed();
    }

    /**
     * Snapshots may cover at most {@link Snapshot#MAX_VOLUME} blocks: a saved template past it is refused while the
     * largest land mark cube reads back, and an architect table or filler placed against a larger volume box takes
     * nothing and leaves it unlocked. The box exists only for the one tick the test runs in.
     */
    public static void snapshotsAndAreasAreSizeBounded(GameTestHelper helper) {
        run(helper, SchematicSafetyTests::snapshotsAndAreasAreSizeBoundedBody);
    }

    private static void snapshotsAndAreasAreSizeBoundedBody(GameTestHelper helper) throws InvalidSnapshotException {
        require(helper, Snapshot.isAllowedSize(new BlockPos(65, 65, 65)) && !Snapshot.isAllowedSize(new BlockPos(81, 81, 81))
                && !Snapshot.isAllowedSize(new BlockPos(256, 256, 256)), "the cap must take a 65-block cube and refuse 81 and 256");
        require(helper, Snapshot.readFromNBT(templateTag(new BlockPos(65, 65, 65))) instanceof Template, "a 65-block cube template must read back");
        boolean bigRefused;
        try {
            Snapshot.readFromNBT(templateTag(new BlockPos(81, 81, 81)));
            bigRefused = false;
        } catch (InvalidSnapshotException e) {
            bigRefused = true;
        }
        require(helper, bigRefused, "a template over the cap must be refused");

        Player placer = helper.makeMockPlayer();
        VolumeBox big = BCTests.placeVolumeBox(helper, placer, new BlockPos(4, 1, 4));
        big.box.extendToEncompass(helper.absolutePos(new BlockPos(4, 2, 4)).offset(80, 80, 80));
        BlockPos tablePos = new BlockPos(3, 2, 4), fillerPos = new BlockPos(4, 2, 3);
        ArchitectTableBlockEntity table = ArchitectTests.placeTable(helper, tablePos, Direction.WEST, placer);
        FillerBlockEntity filler = FillerTests.placeFiller(helper, fillerPos, Direction.NORTH);
        boolean tableValid = table.isValid() || helper.getBlockState(tablePos).getValue(ArchitectTableBlock.VALID);
        boolean fillerTook = filler.hasBox() || filler.templateBuildingInfo() != null;
        boolean locked = !big.locks.isEmpty();
        VolumeBoxes volumeBoxes = VolumeBoxes.get(helper.getLevel());
        volumeBoxes.volumeBoxes.remove(big);
        volumeBoxes.markDirty();
        helper.setBlock(tablePos, Blocks.AIR);
        helper.setBlock(fillerPos, Blocks.AIR);
        require(helper, !tableValid, "an architect table against a box over the cap must stay invalid");
        require(helper, !fillerTook, "a filler against a box over the cap must take nothing");
        require(helper, !locked, "a box over the cap must not be locked by a machine that refused it");
        helper.succeed();
    }

    private static CompoundTag templateTag(BlockPos size) {
        Template template = new Template();
        template.size = size;
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new java.util.BitSet();
        template.computeKey();
        return Snapshot.writeToNBT(template);
    }

    /**
     * Resource-holding state builds back empty, as placing the block's item would: a full composter, a hive full of
     * honey and a charged respawn anchor. Glow lichen costs one per face.
     */
    public static void resourceStateBuildsEmpty(GameTestHelper helper) {
        run(helper, SchematicSafetyTests::resourceStateBuildsEmptyBody);
    }

    private static void resourceStateBuildsEmptyBody(GameTestHelper helper) throws InvalidSnapshotException {
        BlockPos target = new BlockPos(3, 2, 1);
        record Case(BlockState full, net.minecraft.world.level.block.state.properties.IntegerProperty property) {}
        for (Case c : List.of(
                new Case(Blocks.COMPOSTER.defaultBlockState().setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL, 8),
                        net.minecraft.world.level.block.ComposterBlock.LEVEL),
                new Case(Blocks.BEEHIVE.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5),
                        net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL),
                new Case(Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE, 4),
                        net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE))) {
            SchematicBlock read = SchematicBlockManager.readFromNBT(scanned(helper, c.full()));
            helper.setBlock(target, Blocks.AIR);
            read.build(helper.getLevel(), helper.absolutePos(target));
            BlockState built = helper.getBlockState(target);
            require(helper, built.is(c.full().getBlock()) && built.getValue(c.property()) == 0,
                    c.full() + " must build back with " + c.property().getName() + " 0, got " + built);
            require(helper, read.isBuilt(helper.getLevel(), helper.absolutePos(target)), c.full() + " built back empty must count as built");
        }
        helper.setBlock(target, Blocks.AIR);

        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, true)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, true)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.UP, true);
        List<ItemStack> cost = SchematicBlockManager.readFromNBT(scanned(helper, lichen)).computeRequiredItems();
        int count = cost.stream().filter(stack -> stack.is(Items.GLOW_LICHEN)).mapToInt(ItemStack::getCount).sum();
        require(helper, count == 3 && cost.stream().allMatch(stack -> stack.is(Items.GLOW_LICHEN)),
                "glow lichen on three faces must cost three, got " + cost);
        helper.setBlock(AT, Blocks.AIR);
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
