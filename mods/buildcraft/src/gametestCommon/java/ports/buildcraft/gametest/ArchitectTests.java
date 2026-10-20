package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.ArchitectTableBlock;
import ports.buildcraft.builders.ArchitectTableBlockEntity;
import ports.buildcraft.builders.ArchitectTableMenu;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.SnapshotItem;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.SchematicEntity;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotStorage;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.builders.snapshot.rules.JsonRule;
import ports.buildcraft.builders.snapshot.rules.JsonSelector;
import ports.buildcraft.builders.snapshot.rules.RulesLoader;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;

/** Loader-neutral GameTest bodies for the architect table, snapshots, schematics and the builder rules. */
public final class ArchitectTests {
    private static final BlockPos BOX_MIN = new BlockPos(4, 2, 4);
    private static final BlockPos BOX_MAX = new BlockPos(6, 4, 6);
    /** West of {@link #BOX_MIN} and facing west, so the box is behind it. */
    private static final BlockPos TABLE = new BlockPos(3, 2, 4);

    private ArchitectTests() {}

    static ArchitectTableBlockEntity placeTable(GameTestHelper helper, BlockPos pos, Direction facing, Player placer) {
        BlockState state = BCBuilders.ARCHITECT_TABLE.get().defaultBlockState().setValue(ArchitectTableBlock.FACING, facing);
        helper.setBlock(pos, state);
        BCBuilders.ARCHITECT_TABLE.get().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), state, placer, ItemStack.EMPTY);
        return tile(helper, pos, ArchitectTableBlockEntity.class);
    }

    static ArchitectTableBlockEntity tableOnVolumeBox(GameTestHelper helper, Player placer) {
        VolumeBox volumeBox = BCTests.placeVolumeBox(helper, placer, BOX_MIN.below());
        volumeBox.box.extendToEncompass(helper.absolutePos(BOX_MAX));
        return placeTable(helper, TABLE, Direction.WEST, placer);
    }

    static void removeVolumeBoxes(GameTestHelper helper) {
        VolumeBoxes volumeBoxes = VolumeBoxes.get(helper.getLevel());
        volumeBoxes.volumeBoxes.removeIf(box -> box.box.contains(helper.absolutePos(BOX_MIN)));
        volumeBoxes.markDirty();
    }

    static Snapshot storedSnapshot(GameTestHelper helper, ItemStack recorded) {
        Snapshot.Header header = SnapshotItem.getHeader(recorded);
        require(helper, header != null, "a recorded item must carry its snapshot's header");
        Snapshot snapshot = SnapshotStorage.get(helper.getLevel()).getSnapshot(header.key);
        require(helper, snapshot != null, "the recording must be in the snapshot storage");
        return snapshot;
    }

    static SchematicBlock schematic(GameTestHelper helper, BlockPos pos) {
        return SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(helper.getLevel(), helper.absolutePos(BOX_MIN), helper.absolutePos(pos)));
    }

    static boolean costs(List<ItemStack> required, ItemStack... expected) {
        if (required.size() != expected.length) return false;
        List<ItemStack> unmatched = new ArrayList<>(required);
        outer:
        for (ItemStack stack : expected) {
            for (int i = 0; i < unmatched.size(); i++) {
                if (ItemStack.matches(unmatched.get(i), stack)) {
                    unmatched.remove(i);
                    continue outer;
                }
            }
            return false;
        }
        return true;
    }

    static String describe(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.getCount() + "x" + Registry.ITEM.getKey(stack.getItem()) + (stack.hasTag() ? stack.getTag() : "")).toList().toString();
    }

    /**
     * A table placed against a volume box locks it as read, records which blocks are filled onto a template in
     * one tick, keeps the second template for later, and names the recording after the table. Recording the
     * same area again gives the same hash. The glow covers every scanned block.
     */
    public static void architectScansTemplate(GameTestHelper helper) {
        Player placer = helper.makeMockPlayer();
        ArchitectTableBlockEntity table = tableOnVolumeBox(helper, placer);
        VolumeBox volumeBox = VolumeBoxes.get(helper.getLevel()).getVolumeBoxAt(helper.absolutePos(BOX_MIN));
        require(helper, volumeBox != null && volumeBox.isLockedFor(Lock.Remove.class) && volumeBox.isLockedFor(Lock.Resize.class)
                && volumeBox.getLockTargetsStream().anyMatch(new Lock.UsedByMachine(Lock.MachineUse.STRIPES_READ)::equals),
                "the table must lock the box and mark it as read");
        require(helper, table.isValid() && !table.markerBox && helper.getBlockState(TABLE).getValue(ArchitectTableBlock.VALID),
                "a table with a box behind it is valid and leaves the stripes to the box");
        require(helper, table.getOwner().equals(placer.getUUID()), "the placer owns the table");
        helper.setBlock(BOX_MIN, Blocks.STONE);
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.OAK_PLANKS);
        helper.setBlock(BOX_MAX, Blocks.GLASS);
        table.setName("Tower");
        table.setItem(ArchitectTableBlockEntity.SLOT_IN, new ItemStack(BCBuilders.TEMPLATE_CLEAN.get(), 2));
        Set<BlockPos> glowing = new HashSet<>();
        helper.onEachTick(() -> {
            for (long scan : table.getUpdateTag().getLongArray("scans")) glowing.add(BlockPos.of(scan));
        });
        ItemStack[] first = new ItemStack[1];
        helper.startSequence()
                .thenWaitUntil(() -> require(helper, table.getItem(ArchitectTableBlockEntity.SLOT_OUT).is(BCBuilders.TEMPLATE_USED.get()),
                        "the table must record the template"))
                .thenExecute(() -> {
                    first[0] = table.getItem(ArchitectTableBlockEntity.SLOT_OUT).copy();
                    require(helper, table.getItem(ArchitectTableBlockEntity.SLOT_IN).getCount() == 1, "one blank template must be used");
                    Snapshot.Header header = SnapshotItem.getHeader(first[0]);
                    require(helper, header != null && header.name.equals("Tower") && header.owner.equals(placer.getUUID()),
                            "the header must carry the table's name and owner");
                    Snapshot snapshot = storedSnapshot(helper, first[0]);
                    require(helper, snapshot instanceof Template, "a template records a template");
                    Template template = (Template) snapshot;
                    require(helper, template.size.equals(new BlockPos(3, 3, 3)) && template.offset.equals(BlockPos.ZERO) && template.facing == Direction.WEST,
                            "the template must keep the box's size, its offset from the block behind the table and the table's facing");
                    for (BlockPos pos : BlockPos.betweenClosed(BOX_MIN, BOX_MAX)) {
                        BlockPos local = pos.subtract(BOX_MIN);
                        boolean filled = !helper.getBlockState(pos).isAir();
                        require(helper, template.get(local.getX(), local.getY(), local.getZ()) == filled, "the template must record " + local.toShortString());
                    }
                    Set<BlockPos> box = new HashSet<>();
                    BlockPos.betweenClosed(helper.absolutePos(BOX_MIN), helper.absolutePos(BOX_MAX)).forEach(pos -> box.add(pos.immutable()));
                    require(helper, glowing.equals(box), "clients must be sent every scanned block, got " + glowing.size());
                    table.setItem(ArchitectTableBlockEntity.SLOT_OUT, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> require(helper, table.getItem(ArchitectTableBlockEntity.SLOT_OUT).is(BCBuilders.TEMPLATE_USED.get()),
                        "the second template must be recorded once the output is free"))
                .thenExecute(() -> {
                    Snapshot.Header firstHeader = SnapshotItem.getHeader(first[0]);
                    Snapshot.Header secondHeader = SnapshotItem.getHeader(table.getItem(ArchitectTableBlockEntity.SLOT_OUT));
                    require(helper, firstHeader != null && secondHeader != null && firstHeader.key.equals(secondHeader.key),
                            "the same area must record to the same hash");
                    require(helper, table.getItem(ArchitectTableBlockEntity.SLOT_IN).isEmpty(), "both blank templates must be used");
                    removeVolumeBoxes(helper);
                })
                .thenSucceed();
    }

    /**
     * A blueprint records each block's schematic and the entities a capturing rule names, at 300 blocks a tick;
     * what each costs follows the rules. The recording survives a save with the same hash.
     */
    public static void architectScansBlueprint(GameTestHelper helper) {
        ArchitectTableBlockEntity table = tableOnVolumeBox(helper, helper.makeMockPlayer());
        helper.setBlock(BOX_MIN, Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        BCTests.chest(helper, new BlockPos(6, 2, 4), new ItemStack(Items.DIAMOND, 3));
        helper.setBlock(new BlockPos(4, 2, 6), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(4, 3, 6), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 2, 5));
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        helper.spawn(EntityType.PIG, new BlockPos(6, 2, 6));
        table.setItem(ArchitectTableBlockEntity.SLOT_IN, new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get()));
        helper.succeedWhen(() -> {
            ItemStack recorded = table.getItem(ArchitectTableBlockEntity.SLOT_OUT);
            require(helper, recorded.is(BCBuilders.BLUEPRINT_USED.get()), "the table must record the blueprint");
            Snapshot snapshot = storedSnapshot(helper, recorded);
            require(helper, snapshot instanceof Blueprint, "a blueprint records a blueprint");
            Blueprint blueprint = (Blueprint) snapshot;
            require(helper, costs(blueprint.get(0, 0, 0).computeRequiredItems(), new ItemStack(Items.STONE)), "stone costs stone");
            require(helper, blueprint.get(1, 1, 1).isAir(), "empty space records as air");
            List<ItemStack> chest = blueprint.get(2, 0, 0).computeRequiredItems();
            require(helper, costs(chest, new ItemStack(Items.CHEST), new ItemStack(Items.DIAMOND, 3)), "a chest costs itself and its contents, got " + describe(chest));
            require(helper, costs(blueprint.get(0, 0, 2).computeRequiredItems(), new ItemStack(Items.OAK_DOOR))
                    && blueprint.get(0, 1, 2).computeRequiredItems().isEmpty(), "a door costs one door, on its lower half");
            require(helper, blueprint.get(0, 1, 2).getRequiredBlockOffsets().contains(new BlockPos(0, -1, 0)), "a door's upper half waits for the lower half");
            require(helper, blueprint.palette.size() == 6, "the palette holds air, stone, stairs, chest and both door halves, got " + blueprint.palette.size());
            require(helper, blueprint.entities.size() == 1, "only the armour stand has a capturing rule, got " + blueprint.entities.size());
            SchematicEntity standSchematic = blueprint.entities.get(0);
            require(helper, costs(standSchematic.computeRequiredItems(), new ItemStack(Items.ARMOR_STAND), new ItemStack(Items.IRON_HELMET)),
                    "an armour stand costs itself and its armour, got " + describe(standSchematic.computeRequiredItems()));
            require(helper, standSchematic.getPos().distanceTo(stand.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BOX_MIN)))) < 1e-6,
                    "the stand keeps its position relative to the box");

            Snapshot loaded;
            try {
                loaded = Snapshot.readFromNBT(Snapshot.writeToNBT(snapshot));
            } catch (InvalidSnapshotException e) {
                throw new IllegalStateException(e);
            }
            loaded.computeKey();
            require(helper, loaded.key.equals(snapshot.key), "a saved blueprint must load with the same hash");
            removeVolumeBoxes(helper);
        });
    }

    /** Without an area behind it the table goes dark and records nothing; a marked area is taken and its marks removed. */
    public static void architectNeedsAnArea(GameTestHelper helper) {
        ArchitectTableBlockEntity lone = placeTable(helper, new BlockPos(7, 1, 7), Direction.NORTH, helper.makeMockPlayer());
        require(helper, !lone.isValid() && !helper.getBlockState(new BlockPos(7, 1, 7)).getValue(ArchitectTableBlock.VALID),
                "a table with nothing behind it is invalid and dark");
        lone.setItem(ArchitectTableBlockEntity.SLOT_IN, new ItemStack(BCBuilders.TEMPLATE_CLEAN.get()));

        BCTests.landMarkBox(helper, new BlockPos(2, 1, 1), new BlockPos(5, 1, 1), new BlockPos(2, 1, 4));
        ArchitectTableBlockEntity marked = placeTable(helper, new BlockPos(1, 1, 1), Direction.WEST, helper.makeMockPlayer());
        require(helper, marked.isValid() && marked.markerBox, "a table on a marked area is valid and draws its stripes");
        require(helper, marked.box.min().equals(helper.absolutePos(new BlockPos(2, 1, 1))) && marked.box.max().equals(helper.absolutePos(new BlockPos(5, 1, 4))),
                "the table must take the marks' box, got " + marked.box.min() + " to " + marked.box.max());
        for (BlockPos mark : List.of(new BlockPos(2, 1, 1), new BlockPos(5, 1, 1), new BlockPos(2, 1, 4))) {
            require(helper, helper.getBlockState(mark).isAir(), "the table must remove the land mark at " + mark);
        }
        helper.runAfterDelay(10, () -> {
            require(helper, lone.getItem(ArchitectTableBlockEntity.SLOT_OUT).isEmpty() && !lone.isScanning(), "an invalid table must not record");
            helper.succeed();
        });
    }

    /**
     * The menu's slots take only templates and blueprints and give nothing back in; automation inserts into the
     * input and extracts from the output. The name message renames the open table, refusing names longer than
     * upstream's field. Everything survives a save, and clients get the area, name and progress.
     */
    public static void architectMenuNameAndSaving(GameTestHelper helper) {
        ArchitectTableBlockEntity table = tableOnVolumeBox(helper, helper.makeMockPlayer());
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "architect"), null);
        BlockPos absolute = helper.absolutePos(TABLE);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        ArchitectTableMenu menu = new ArchitectTableMenu(0, player.getInventory(), table);
        int in = 36 + ArchitectTableBlockEntity.SLOT_IN, out = 36 + ArchitectTableBlockEntity.SLOT_OUT;
        require(helper, menu.getSlot(in).mayPlace(new ItemStack(BCBuilders.TEMPLATE_CLEAN.get())) && menu.getSlot(in).mayPlace(new ItemStack(BCBuilders.BLUEPRINT_USED.get()))
                && !menu.getSlot(in).mayPlace(new ItemStack(Items.PAPER)) && !menu.getSlot(out).mayPlace(new ItemStack(BCBuilders.TEMPLATE_CLEAN.get())),
                "the input takes any template or blueprint and the output takes nothing");
        ItemStack blank = new ItemStack(BCBuilders.TEMPLATE_CLEAN.get());
        require(helper, table.canPlaceItemThroughFace(ArchitectTableBlockEntity.SLOT_IN, blank, Direction.UP)
                && !table.canPlaceItemThroughFace(ArchitectTableBlockEntity.SLOT_OUT, blank, Direction.UP)
                && table.canTakeItemThroughFace(ArchitectTableBlockEntity.SLOT_OUT, blank, Direction.DOWN)
                && !table.canTakeItemThroughFace(ArchitectTableBlockEntity.SLOT_IN, blank, Direction.DOWN),
                "automation fills the input and empties the output");

        player.containerMenu = menu;
        FriendlyByteBuf name = new FriendlyByteBuf(Unpooled.buffer());
        name.writeUtf("Lighthouse");
        ArchitectTableMenu.receiveName(player, name);
        require(helper, table.name.equals("Lighthouse"), "the name message must rename the open table, got " + table.name);
        FriendlyByteBuf tooLong = new FriendlyByteBuf(Unpooled.buffer());
        tooLong.writeUtf("x".repeat(ArchitectTableMenu.MAX_NAME_LENGTH + 1));
        boolean refused;
        try {
            ArchitectTableMenu.receiveName(player, tooLong);
            refused = false;
        } catch (RuntimeException e) {
            refused = true;
        }
        require(helper, refused && table.name.equals("Lighthouse"), "a name longer than the field allows must be refused");

        table.setItem(ArchitectTableBlockEntity.SLOT_OUT, new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get()));
        CompoundTag saved = table.saveWithoutMetadata();
        ArchitectTableBlockEntity loaded = new ArchitectTableBlockEntity(absolute, table.getBlockState());
        loaded.load(saved);
        require(helper, loaded.box.min().equals(table.box.min()) && loaded.box.max().equals(table.box.max()) && loaded.isValid() && !loaded.markerBox,
                "saving must keep the area");
        require(helper, loaded.name.equals("Lighthouse") && loaded.getOwner().equals(table.getOwner()), "saving must keep the name and owner");
        require(helper, loaded.getItem(ArchitectTableBlockEntity.SLOT_OUT).is(BCBuilders.BLUEPRINT_CLEAN.get()), "saving must keep the items");
        CompoundTag update = table.getUpdateTag();
        require(helper, update.contains("box") && update.getString("name").equals("Lighthouse") && update.contains("progress"),
                "clients must receive the area, name and progress");
        removeVolumeBoxes(helper);
        helper.succeed();
    }

    /** Templates are stored by hash, listed, and removed; a stored template reads back equal. */
    public static void snapshotStorageRoundTrip(GameTestHelper helper) {
        Template template = new Template();
        template.size = new BlockPos(2, 3, 4);
        template.facing = Direction.EAST;
        template.offset = new BlockPos(1, 0, -2);
        template.data.set(0);
        template.data.set(5);
        template.data.set(23);
        template.computeKey();
        SnapshotStorage storage = SnapshotStorage.get(helper.getLevel());
        storage.addSnapshot(template);
        require(helper, storage.getList().contains(template.key), "a stored template must be listed");
        Snapshot read = storage.getSnapshot(template.key);
        require(helper, read instanceof Template stored && stored.data.equals(template.data) && stored.size.equals(template.size)
                && stored.facing == Direction.EAST && stored.offset.equals(template.offset), "a stored template must read back equal");
        Template other = template.copy();
        other.data.set(1);
        other.computeKey();
        require(helper, !other.key.equals(template.key), "different contents must hash differently");
        storage.removeSnapshot(template.key);
        require(helper, !storage.getList().contains(template.key), "a removed template must no longer be listed");
        helper.succeed();
    }

    /**
     * Every rule names blocks, entities, properties and items that exist in 1.19.2, and the rules give the
     * flattened blocks upstream's costs and dependencies.
     */
    public static void builderRulesCover119Blocks(GameTestHelper helper) {
        List<JsonRule> rules = RulesLoader.rules();
        require(helper, rules.size() >= 300, "the vanilla rules must load, got " + rules.size());
        require(helper, RulesLoader.readDomains().contains("minecraft") && RulesLoader.readDomains().stream().noneMatch(domain -> domain.startsWith("buildcraft")),
                "blueprints record Minecraft's blocks and never BuildCraft's, got " + RulesLoader.readDomains());
        for (JsonRule rule : rules) {
            for (JsonSelector selector : rule.selectors == null ? List.<JsonSelector>of() : rule.selectors) {
                if (rule.capture) {
                    require(helper, Registry.ENTITY_TYPE.containsKey(new ResourceLocation(selector.base)), "no entity " + selector.base);
                } else {
                    requireBlockSelector(helper, selector.base);
                }
            }
            for (String block : rule.canBeReplacedWithBlocks == null ? List.<String>of() : rule.canBeReplacedWithBlocks) {
                require(helper, Registry.BLOCK.containsKey(new ResourceLocation(block)), "no block " + block);
            }
            if (rule.placeBlock != null) require(helper, Registry.BLOCK.containsKey(new ResourceLocation(rule.placeBlock)), "no block " + rule.placeBlock);
        }
        for (ResourceLocation item : RulesLoader.literalItems()) require(helper, Registry.ITEM.containsKey(item), "no item " + item);

        BlockPos pos = new BlockPos(4, 2, 4);
        Function<BlockState, SchematicBlock> read = state -> {
            helper.setBlock(pos, state);
            return schematic(helper, pos);
        };
        requireCost(helper, read.apply(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)), new ItemStack(Items.OAK_SLAB, 2));
        requireCost(helper, read.apply(Blocks.MUD_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)), new ItemStack(Items.MUD_BRICK_SLAB, 2));
        requireCost(helper, read.apply(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5)), new ItemStack(Items.SNOW, 5));
        ItemStack water = new ItemStack(Items.POTION, 2);
        water.getOrCreateTag().putString("Potion", "minecraft:water");
        requireCost(helper, read.apply(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2)),
                new ItemStack(Items.CAULDRON), water);
        requireCost(helper, read.apply(Blocks.LAVA_CAULDRON.defaultBlockState()), new ItemStack(Items.CAULDRON), new ItemStack(Items.LAVA_BUCKET));
        requireCost(helper, read.apply(Blocks.POTTED_POPPY.defaultBlockState()), new ItemStack(Items.FLOWER_POT), new ItemStack(Items.POPPY));
        requireCost(helper, read.apply(Blocks.FARMLAND.defaultBlockState()), new ItemStack(Items.DIRT));
        requireCost(helper, read.apply(Blocks.WHEAT.defaultBlockState()), new ItemStack(Items.WHEAT_SEEDS));
        requireCost(helper, read.apply(Blocks.WARPED_WALL_SIGN.defaultBlockState()), new ItemStack(Items.WARPED_SIGN));
        requireCost(helper, read.apply(Blocks.RED_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3)), new ItemStack(Items.RED_CANDLE, 3));
        requireCost(helper, read.apply(Blocks.MANGROVE_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        requireCost(helper, read.apply(Blocks.PISTON_HEAD.defaultBlockState()));

        require(helper, read.apply(Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.CEILING)).getRequiredBlockOffsets()
                .equals(Set.of(new BlockPos(0, 1, 0))), "a ceiling lever needs the block above it");
        require(helper, read.apply(Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.EAST))
                .getRequiredBlockOffsets().equals(Set.of(new BlockPos(-1, 0, 0))), "a wall lever needs the block it hangs on");
        require(helper, read.apply(Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH)).getRequiredBlockOffsets()
                .equals(Set.of(new BlockPos(0, 0, 1))), "a wall torch needs the block it hangs on");
        require(helper, read.apply(Blocks.SAND.defaultBlockState()).getRequiredBlockOffsets().equals(Set.of(new BlockPos(0, -1, 0))),
                "falling blocks need the block below them");
        require(helper, read.apply(Blocks.FIRE.defaultBlockState()).isAir(), "fire is ignored");
        require(helper, read.apply(BCBuilders.FILLER.get().defaultBlockState()).isAir(), "BuildCraft blocks are never recorded");
        SchematicBlock repeater = read.apply(Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.POWERED, true));
        helper.setBlock(pos, Blocks.REPEATER.defaultBlockState());
        require(helper, repeater.isBuilt(helper.getLevel(), helper.absolutePos(pos)), "an unpowered repeater counts as a built powered one");
        helper.succeed();
    }

    private static void requireBlockSelector(GameTestHelper helper, String base) {
        int bracket = base.indexOf('[');
        ResourceLocation id = new ResourceLocation(bracket < 0 ? base : base.substring(0, bracket));
        Optional<Block> block = Registry.BLOCK.getOptional(id);
        require(helper, block.isPresent(), "no block " + id);
        if (bracket < 0) return;
        for (String pair : base.substring(bracket + 1, base.indexOf(']')).split(", ")) {
            String[] split = pair.split("=");
            Property<?> property = block.get().getStateDefinition().getProperty(split[0]);
            require(helper, property != null && property.getValue(split[1]).isPresent(), base + " names a property value " + id + " does not have");
        }
    }

    private static void requireCost(GameTestHelper helper, SchematicBlock schematic, ItemStack... expected) {
        List<ItemStack> required = schematic.computeRequiredItems();
        require(helper, costs(required, expected), "expected " + describe(List.of(expected)) + ", got " + describe(required));
    }

    /**
     * Recorded blocks build back as they were: stairs keep their facing, a chest its contents, and a wall
     * torch waits for its wall. Building is checked against what is already there.
     */
    public static void schematicsBuildBack(GameTestHelper helper) {
        BlockPos stone = new BlockPos(4, 2, 4), torch = new BlockPos(4, 2, 5), stairs = new BlockPos(5, 2, 4), chest = new BlockPos(6, 2, 4);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        helper.setBlock(stairs, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        helper.setBlock(chest, Blocks.CHEST);
        BCTests.chest(helper, chest, new ItemStack(Items.EMERALD, 7));
        List<BlockPos> order = List.of(stone, torch, stairs, chest);
        List<BlockState> states = order.stream().map(helper::getBlockState).toList();
        List<SchematicBlock> schematics = order.stream().map(pos -> schematic(helper, pos)).toList();
        require(helper, schematics.get(1).getRequiredBlockOffsets().contains(new BlockPos(0, 0, -1)), "a south-facing wall torch needs the block north of it");
        for (BlockPos pos : order) helper.setBlock(pos, Blocks.AIR);
        for (int i = 0; i < order.size(); i++) {
            BlockPos absolute = helper.absolutePos(order.get(i));
            SchematicBlock schematic = schematics.get(i);
            require(helper, schematic.canBuild(helper.getLevel(), absolute) && !schematic.isBuilt(helper.getLevel(), absolute), "an empty space can be built");
            require(helper, schematic.build(helper.getLevel(), absolute), "building " + order.get(i) + " must succeed");
            require(helper, schematic.isBuilt(helper.getLevel(), absolute) && helper.getBlockState(order.get(i)).equals(states.get(i)),
                    "building must restore " + states.get(i) + ", got " + helper.getBlockState(order.get(i)));
        }
        ChestBlockEntity rebuilt = tile(helper, chest, ChestBlockEntity.class);
        require(helper, rebuilt.getItem(0).is(Items.EMERALD) && rebuilt.getItem(0).getCount() == 7, "the chest must be rebuilt with its contents");
        helper.succeed();
    }

    /** The template, blueprint and table recipes follow upstream's, with conventional and Forge tags. */
    public static void architectRecipes(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        requireRecipe(helper, recipes.byKey(BuildCraft.id("template")).orElse(null), BCBuilders.TEMPLATE_CLEAN.get());
        requireRecipe(helper, recipes.byKey(BuildCraft.id("blueprint")).orElse(null), BCBuilders.BLUEPRINT_CLEAN.get());
        requireRecipe(helper, recipes.byKey(BuildCraft.id("architect_table")).orElse(null), BCBuilders.ARCHITECT_TABLE_ITEM.get());
        CraftingContainer grid = new CraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        }, 3, 3);
        for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(Items.PAPER));
        grid.setItem(4, new ItemStack(Items.LAPIS_LAZULI));
        CraftingRecipe blueprint = (CraftingRecipe) recipes.byKey(BuildCraft.id("blueprint")).orElseThrow();
        require(helper, blueprint.matches(grid, helper.getLevel()) && blueprint.assemble(grid).is(BCBuilders.BLUEPRINT_CLEAN.get()),
                "eight paper around lapis must craft a blueprint");
        grid.setItem(4, new ItemStack(Items.BLACK_DYE));
        CraftingRecipe template = (CraftingRecipe) recipes.byKey(BuildCraft.id("template")).orElseThrow();
        require(helper, template.matches(grid, helper.getLevel()), "eight paper around black dye must craft a template");
        require(helper, new ItemStack(BCBuilders.TEMPLATE_CLEAN.get()).getMaxStackSize() == 16 && new ItemStack(BCBuilders.TEMPLATE_USED.get()).getMaxStackSize() == 1,
                "blank templates stack to 16 and recorded ones do not stack");
        require(helper, ((SnapshotItem) BCBuilders.BLUEPRINT_USED.get()).snapshotType == EnumSnapshotType.BLUEPRINT, "the blueprint item records blueprints");
        helper.succeed();
    }

    private static void requireRecipe(GameTestHelper helper, Recipe<?> recipe, Item result) {
        require(helper, recipe != null, "missing recipe for " + Registry.ITEM.getKey(result));
        require(helper, recipe.getResultItem().is(result), "recipe must make " + Registry.ITEM.getKey(result) + ", makes " + recipe.getResultItem());
    }
}
