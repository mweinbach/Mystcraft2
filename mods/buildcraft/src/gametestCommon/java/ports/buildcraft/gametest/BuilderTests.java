package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.ArchitectTests.costs;
import static ports.buildcraft.gametest.ArchitectTests.describe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.BuilderBlock;
import ports.buildcraft.builders.BuilderBlockEntity;
import ports.buildcraft.builders.BuilderMenu;
import ports.buildcraft.builders.EnumOptionalSnapshotType;
import ports.buildcraft.builders.SnapshotItem;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.SchematicEntity;
import ports.buildcraft.builders.snapshot.SchematicEntityContext;
import ports.buildcraft.builders.snapshot.SchematicEntityManager;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotStorage;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.MarkerPathBlockEntity;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Tank;

/** Loader-neutral GameTest bodies for the builder. */
public final class BuilderTests {
    private static final BlockPos PATH_START = new BlockPos(2, 2, 1), PATH_END = new BlockPos(2, 2, 4);
    private static final List<BlockPos> ALONG_PATH = List.of(PATH_START, new BlockPos(2, 2, 2), new BlockPos(2, 2, 3), PATH_END);

    private BuilderTests() {}

    static BuilderBlockEntity placeBuilder(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState state = BCBuilders.BUILDER.get().defaultBlockState().setValue(BuilderBlock.FACING, facing);
        helper.setBlock(pos, state);
        BCBuilders.BUILDER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), state, null, ItemStack.EMPTY);
        return tile(helper, pos, BuilderBlockEntity.class);
    }

    /**
     * Records the area from {@code min} as the architect table does, as if from a table facing {@code facing}
     * with {@code min} behind it, and returns the recorded item.
     */
    static ItemStack record(GameTestHelper helper, EnumSnapshotType type, BlockPos min, BlockPos size, Direction facing) {
        return record(helper.getLevel(), type, helper.absolutePos(min), size, facing, "Test");
    }

    static ItemStack record(Level level, EnumSnapshotType type, BlockPos absoluteMin, BlockPos size, Direction facing, String name) {
        Snapshot snapshot = Snapshot.create(type);
        snapshot.size = size;
        snapshot.facing = facing;
        snapshot.offset = BlockPos.ZERO;
        BitSet filled = new BitSet(Snapshot.getDataSize(size));
        List<SchematicBlock> palette = new ArrayList<>();
        int[] data = new int[Snapshot.getDataSize(size)];
        for (BlockPos local : BlockPos.betweenClosed(BlockPos.ZERO, size.offset(-1, -1, -1))) {
            BlockPos world = absoluteMin.offset(local);
            int index = Snapshot.posToIndex(size, local);
            filled.set(index, !level.getBlockState(world).isAir());
            SchematicBlock schematicBlock = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(level, absoluteMin, world));
            int paletteIndex = palette.indexOf(schematicBlock);
            if (paletteIndex == -1) {
                paletteIndex = palette.size();
                palette.add(schematicBlock);
            }
            data[index] = paletteIndex;
        }
        if (snapshot instanceof Template template) template.data = filled;
        if (snapshot instanceof Blueprint blueprint) {
            blueprint.palette.addAll(palette);
            blueprint.data = data;
            for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(absoluteMin, absoluteMin.offset(size)))) {
                SchematicEntity schematicEntity = SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(level, absoluteMin, entity));
                if (schematicEntity != null) blueprint.entities.add(schematicEntity);
            }
        }
        snapshot.computeKey();
        SnapshotStorage.get(level).addSnapshot(snapshot);
        return SnapshotItem.getUsed(type, new Snapshot.Header(snapshot.key, new UUID(0, 0), new Date(), name));
    }

    static void keepCharged(GameTestHelper helper, BuilderBlockEntity builder) {
        helper.onEachTick(() -> builder.battery().set(builder.battery().capacity()));
    }

    static int resourceCount(BuilderBlockEntity builder) {
        int count = 0;
        for (int slot = 1; slot <= BuilderBlockEntity.RESOURCE_SLOTS; slot++) count += builder.getItem(slot).getCount();
        return count;
    }

    /** Dropped stacks are split into item entities of random sizes, so their counts are summed. */
    static boolean dropped(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(1),
                item -> ItemStack.isSameItemSameTags(item.getItem(), stack)).stream().mapToInt(item -> item.getItem().getCount()).sum() == stack.getCount();
    }

    /**
     * A template recorded facing west, held by a builder facing north, is turned a quarter clockwise and built
     * behind the builder from any block in its resources; blocks where the template is empty are dug out.
     * The slot model follows the snapshot.
     */
    public static void builderBuildsRotatedTemplate(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(4, 2, 4), new BlockPos(5, 2, 6))) helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 6), Blocks.AIR);
        ItemStack template = record(helper, EnumSnapshotType.TEMPLATE, new BlockPos(4, 2, 4), new BlockPos(2, 1, 3), Direction.WEST);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(4, 2, 4), new BlockPos(5, 2, 6))) helper.setBlock(pos, Blocks.AIR);

        BlockPos builderPos = new BlockPos(6, 2, 2);
        BuilderBlockEntity builder = placeBuilder(helper, builderPos, Direction.NORTH);
        require(helper, builder.getBuilder() == null && helper.getBlockState(builderPos).getValue(BuilderBlock.SNAPSHOT_TYPE) == EnumOptionalSnapshotType.NONE,
                "an empty builder has nothing to build and shows an empty slot");
        BlockPos dug = new BlockPos(4, 2, 4);
        helper.setBlock(dug, Blocks.DIRT);
        builder.setItem(1, new ItemStack(Items.STONE, 64));
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, template);
        require(helper, builder.getBuilder() == builder.templateBuilder && builder.getRotation() == Rotation.CLOCKWISE_90,
                "a template facing west in a builder facing north turns a quarter clockwise, got " + builder.getRotation());
        require(helper, builder.getBox().min().equals(helper.absolutePos(new BlockPos(4, 2, 3))) && builder.getBox().max().equals(helper.absolutePos(new BlockPos(6, 2, 4))),
                "the rotated template must sit behind the builder, got " + builder.getBox().min() + " to " + builder.getBox().max());
        require(helper, helper.getBlockState(builderPos).getValue(BuilderBlock.SNAPSHOT_TYPE) == EnumOptionalSnapshotType.TEMPLATE,
                "the slot must show a template");
        List<BlockPos> filled = List.of(new BlockPos(6, 2, 3), new BlockPos(5, 2, 3), new BlockPos(4, 2, 3), new BlockPos(6, 2, 4), new BlockPos(5, 2, 4));
        keepCharged(helper, builder);
        helper.succeedWhen(() -> {
            for (BlockPos pos : filled) require(helper, helper.getBlockState(pos).is(Blocks.STONE), "the template needs stone at " + pos);
            require(helper, helper.getBlockState(dug).isAir(), "the builder must dig out the dirt where the template is empty");
            require(helper, builder.getItem(1).getCount() == 64 - filled.size(), "each block must use one stone, " + builder.getItem(1).getCount() + " left");
            require(helper, builder.isDone(), "a built template leaves the builder done");
        });
    }

    /**
     * A blueprint's blocks are built turned with the builder, a chest with its contents, each only once every
     * item it costs is in the resources. Until then the display lists what is still needed, with counts,
     * and clients get the list.
     */
    public static void builderBuildsBlueprint(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        BCTests.chest(helper, new BlockPos(6, 2, 4), new ItemStack(Items.DIAMOND, 3));
        ItemStack blueprint = record(helper, EnumSnapshotType.BLUEPRINT, new BlockPos(4, 2, 4), new BlockPos(3, 1, 1), Direction.WEST);
        tile(helper, new BlockPos(6, 2, 4), ChestBlockEntity.class).clearContent();
        for (int x = 4; x <= 6; x++) helper.setBlock(new BlockPos(x, 2, 4), Blocks.AIR);

        BlockPos builderPos = new BlockPos(2, 2, 2);
        BlockPos stone = new BlockPos(2, 2, 3), stairs = new BlockPos(2, 2, 4), chest = new BlockPos(2, 2, 5);
        BuilderBlockEntity builder = placeBuilder(helper, builderPos, Direction.NORTH);
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, blueprint);
        require(helper, builder.getBuilder() == builder.blueprintBuilder
                && helper.getBlockState(builderPos).getValue(BuilderBlock.SNAPSHOT_TYPE) == EnumOptionalSnapshotType.BLUEPRINT,
                "a blueprint in the slot is built by the blueprint builder and shown in the slot");
        keepCharged(helper, builder);
        helper.startSequence()
                .thenWaitUntil(() -> require(helper, costs(builder.getDisplayRequired(), new ItemStack(Items.STONE), new ItemStack(Items.OAK_STAIRS),
                        new ItemStack(Items.CHEST), new ItemStack(Items.DIAMOND, 3)), "the display must list the whole cost, got " + describe(builder.getDisplayRequired())))
                .thenExecute(() -> {
                    require(helper, helper.getBlockState(stone).isAir(), "nothing is built without resources");
                    builder.setItem(1, new ItemStack(Items.STONE));
                    builder.setItem(2, new ItemStack(Items.OAK_STAIRS));
                    builder.setItem(3, new ItemStack(Items.CHEST));
                })
                .thenWaitUntil(() -> {
                    require(helper, helper.getBlockState(stone).is(Blocks.STONE), "the stone must be built");
                    require(helper, helper.getBlockState(stairs).equals(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)),
                            "stairs facing east turn to face south, got " + helper.getBlockState(stairs));
                    require(helper, costs(builder.getDisplayRequired(), new ItemStack(Items.CHEST), new ItemStack(Items.DIAMOND, 3)),
                            "only the chest and its contents are still needed, got " + describe(builder.getDisplayRequired()));
                })
                .thenExecute(() -> {
                    require(helper, helper.getBlockState(chest).isAir() && builder.getItem(3).is(Items.CHEST),
                            "the chest waits for its contents and keeps its item");
                    ListTag synced = builder.getUpdateTag().getList("remainingDisplayRequired", Tag.TAG_COMPOUND);
                    require(helper, synced.stream().map(CompoundTag.class::cast).anyMatch(tag -> ItemStack.of(tag).is(Items.DIAMOND) && tag.getInt("BuilderCount") == 3),
                            "clients must receive what is still needed, with counts");
                    builder.setItem(4, new ItemStack(Items.DIAMOND, 3));
                })
                .thenWaitUntil(() -> {
                    BlockState chestState = helper.getBlockState(chest);
                    require(helper, chestState.is(Blocks.CHEST) && chestState.getValue(ChestBlock.FACING) == Direction.EAST,
                            "a chest facing north turns to face east, got " + chestState);
                    require(helper, BCTests.count(tile(helper, chest, ChestBlockEntity.class), Items.DIAMOND) == 3, "the chest is rebuilt with its contents");
                    require(helper, resourceCount(builder) == 0 && builder.getDisplayRequired().isEmpty(), "every resource is used and nothing is still needed");
                    require(helper, builder.isDone(), "a built blueprint leaves the builder done");
                })
                .thenSucceed();
    }

    /** A fluid source in a blueprint is placed from the builder's tanks, which the display asks for as a bucket. */
    public static void builderPlacesFluidsFromTanks(GameTestHelper helper) {
        BlockPos water = new BlockPos(5, 2, 5);
        for (BlockPos wall : List.of(new BlockPos(4, 2, 5), new BlockPos(6, 2, 5), new BlockPos(5, 2, 6), new BlockPos(5, 2, 4))) helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(water, Blocks.WATER);
        ItemStack blueprint = record(helper, EnumSnapshotType.BLUEPRINT, water, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.setBlock(water, Blocks.AIR);

        BuilderBlockEntity builder = placeBuilder(helper, new BlockPos(5, 2, 4), Direction.NORTH);
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, blueprint);
        keepCharged(helper, builder);
        helper.startSequence()
                .thenWaitUntil(() -> require(helper, costs(builder.getDisplayRequired(), new ItemStack(Items.WATER_BUCKET)),
                        "the display must ask for water as a bucket, got " + describe(builder.getDisplayRequired())))
                .thenExecute(() -> {
                    require(helper, helper.getBlockState(water).isAir(), "nothing is placed without fluid");
                    require(helper, builder.fluidPort(Direction.UP).fill(Fluids.WATER, 1500, false) == 1500, "the tanks take water");
                })
                .thenWaitUntil(() -> {
                    require(helper, helper.getBlockState(water).getFluidState().isSource() && helper.getBlockState(water).getFluidState().is(Fluids.WATER), "the water source must be placed");
                    require(helper, builder.getTanks().get(0).contents().equals(new FluidAmount(Fluids.WATER, 500)),
                            "a bucket's worth must be drained, got " + builder.getTanks().get(0).contents());
                    require(helper, builder.getDisplayRequired().isEmpty() && builder.isDone(), "nothing is still needed");
                })
                .thenSucceed();
    }

    /**
     * With a full battery the builder removes entities in its area that the blueprint does not have, if a rule
     * captures them, and spawns the blueprint's entities from their items, turned with the builder.
     */
    public static void builderSpawnsAndRemovesEntities(GameTestHelper helper) {
        ArmorStand recorded = helper.spawn(EntityType.ARMOR_STAND, new Vec3(4.5, 2, 5.5));
        recorded.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        ItemStack blueprint = record(helper, EnumSnapshotType.BLUEPRINT, new BlockPos(4, 2, 4), new BlockPos(3, 2, 3), Direction.WEST);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(6.5, 2, 6.5));
        pig.setNoAi(true);

        BuilderBlockEntity builder = placeBuilder(helper, new BlockPos(6, 2, 3), Direction.NORTH);
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, blueprint);
        Vec3 target = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)).add(5.5, 2, 4.5);
        keepCharged(helper, builder);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    require(helper, recorded.isRemoved(), "the builder must remove the armour stand that is not where the turned blueprint has one");
                    require(helper, costs(builder.getDisplayRequired(), new ItemStack(Items.ARMOR_STAND), new ItemStack(Items.IRON_HELMET)),
                            "the display must list the stand and its armour, got " + describe(builder.getDisplayRequired()));
                })
                .thenExecute(() -> {
                    builder.setItem(1, new ItemStack(Items.ARMOR_STAND));
                    builder.setItem(2, new ItemStack(Items.IRON_HELMET));
                })
                .thenWaitUntil(() -> {
                    List<ArmorStand> stands = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(target, target).inflate(0.2));
                    require(helper, stands.size() == 1 && stands.get(0).getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                            "the builder must spawn the armour stand, with its helmet, a quarter turn round");
                    require(helper, resourceCount(builder) == 0 && builder.getDisplayRequired().isEmpty(), "the stand uses its items");
                })
                .thenExecute(() -> require(helper, pig.isAlive(), "entities no rule captures are left alone"))
                .thenSucceed();
    }

    /**
     * Placed with a path mark behind it, the builder takes the path, removes the marks and builds its snapshot
     * at every block along the path in turn, stopping at the last.
     */
    public static void builderFollowsPathMarks(GameTestHelper helper) {
        BuilderBlockEntity builder = pathBuilder(helper);
        require(helper, builder.path != null && builder.path.equals(List.of(helper.absolutePos(PATH_START), helper.absolutePos(PATH_END))),
                "the builder must take the path, got " + builder.path);
        require(helper, builder.getBasePoses().equals(ALONG_PATH.stream().map(helper::absolutePos).toList()),
                "the builder must build at every block along the path, got " + builder.getBasePoses());
        require(helper, helper.getBlockState(PATH_START).isAir() && helper.getBlockState(PATH_END).isAir(), "the builder must remove the path marks");
        keepCharged(helper, builder);
        helper.succeedWhen(() -> {
            for (BlockPos pos : ALONG_PATH) require(helper, helper.getBlockState(pos).is(Blocks.STONE), "the builder must build at " + pos);
            require(helper, builder.getCurrentBasePosIndex() == ALONG_PATH.size() - 1 && builder.isDone(), "the builder must stop at the end of the path");
            require(helper, builder.getItem(1).getCount() == 64 - ALONG_PATH.size(), "each step must use one stone");
        });
    }

    /**
     * As upstream, a step counts as done once all of it is queued, so the builder moves on and cancels a placement
     * still waiting for power: with an empty battery it skips every step but the last, which waits for power.
     */
    public static void builderSkipsPathStepsItCannotPowerAtOnce(GameTestHelper helper) {
        BuilderBlockEntity builder = pathBuilder(helper);
        helper.startSequence()
                .thenWaitUntil(() -> require(helper, builder.getCurrentBasePosIndex() == ALONG_PATH.size() - 1,
                        "an unpowered builder must still step to the end of the path, got step " + builder.getCurrentBasePosIndex()))
                .thenExecute(() -> {
                    for (BlockPos pos : ALONG_PATH) require(helper, helper.getBlockState(pos).isAir(), "nothing is built without power at " + pos);
                    require(helper, builder.getItem(1).getCount() == 64, "cancelled placements must return their stone");
                    keepCharged(helper, builder);
                })
                .thenWaitUntil(() -> {
                    require(helper, helper.getBlockState(PATH_END).is(Blocks.STONE), "the last step must be built once powered");
                    for (BlockPos pos : ALONG_PATH.subList(0, ALONG_PATH.size() - 1)) {
                        require(helper, helper.getBlockState(pos).isAir(), "skipped steps stay unbuilt at " + pos);
                    }
                    require(helper, builder.getItem(1).getCount() == 63 && builder.isDone(), "only the last step uses stone");
                })
                .thenSucceed();
    }

    /** A builder facing west with path marks behind it from {@link #PATH_START} to {@link #PATH_END}, holding stone and a one-block template. */
    private static BuilderBlockEntity pathBuilder(GameTestHelper helper) {
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.STONE);
        ItemStack template = record(helper, EnumSnapshotType.TEMPLATE, new BlockPos(7, 2, 7), new BlockPos(1, 1, 1), Direction.WEST);
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.AIR);
        BCTests.marker(helper, PATH_START, BCCore.MARKER_PATH.get(), MarkerPathBlockEntity.class);
        BCTests.marker(helper, PATH_END, BCCore.MARKER_PATH.get(), MarkerPathBlockEntity.class);
        require(helper, MarkerCaches.PATH.getSubCache(helper.getLevel()).tryConnect(helper.absolutePos(PATH_START), helper.absolutePos(PATH_END)),
                "the marks must connect");
        BuilderBlockEntity builder = placeBuilder(helper, new BlockPos(1, 2, 1), Direction.WEST);
        builder.setItem(1, new ItemStack(Items.STONE, 64));
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, template);
        return builder;
    }

    /**
     * The snapshot slot takes one recorded template or blueprint and the resources take anything, from the menu
     * and from automation; the tanks fill in turn. A wrench turns the builder only while it has nothing to
     * build. Everything survives a save, clients get the area and snapshot type, and breaking the builder
     * drops its items.
     */
    public static void builderMenuSavingAndDrops(GameTestHelper helper) {
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.STONE);
        ItemStack template = record(helper, EnumSnapshotType.TEMPLATE, new BlockPos(7, 2, 7), new BlockPos(1, 1, 1), Direction.WEST);
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.AIR);
        BlockPos pos = new BlockPos(4, 2, 4);
        BuilderBlockEntity builder = placeBuilder(helper, pos, Direction.NORTH);
        Player player = helper.makeMockPlayer();
        BuilderMenu menu = new BuilderMenu(0, player.getInventory(), builder);
        ItemStack blank = new ItemStack(BCBuilders.TEMPLATE_CLEAN.get());
        require(helper, menu.getSlot(36).mayPlace(template) && !menu.getSlot(36).mayPlace(blank) && !menu.getSlot(36).mayPlace(new ItemStack(Items.STONE))
                && menu.getSlot(36).getMaxStackSize() == 1, "the snapshot slot takes one recorded template or blueprint");
        require(helper, menu.getSlot(37).mayPlace(new ItemStack(Items.STICK)) && menu.slots.size() == 36 + 1 + BuilderBlockEntity.RESOURCE_SLOTS,
                "the 27 resource slots take anything");
        require(helper, builder.canPlaceItemThroughFace(BuilderBlockEntity.SLOT_SNAPSHOT, template, Direction.UP)
                && !builder.canPlaceItemThroughFace(BuilderBlockEntity.SLOT_SNAPSHOT, blank, Direction.UP)
                && builder.canPlaceItemThroughFace(5, new ItemStack(Items.STICK), Direction.DOWN)
                && builder.canTakeItemThroughFace(5, new ItemStack(Items.STICK), Direction.NORTH), "automation reaches every slot from every side");

        require(helper, builder.fluidPort(null).fill(Fluids.WATER, 10_000, false) == 10_000
                && builder.fluidPort(null).fill(Fluids.LAVA, 500, false) == 500, "the tanks take water and lava");
        List<Tank> tanks = builder.getTanks();
        require(helper, tanks.get(0).contents().equals(new FluidAmount(Fluids.WATER, 8000)) && tanks.get(1).contents().equals(new FluidAmount(Fluids.WATER, 2000))
                && tanks.get(2).contents().equals(new FluidAmount(Fluids.LAVA, 500)), "each tank fills in turn and keeps to one fluid");
        require(helper, menu.tankAmount(1) == 2000 && menu.tankFluid(2) == Fluids.LAVA, "the menu reports the tanks");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCore.WRENCH.get()));
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, template.copy());
        require(helper, BCBuilders.BUILDER.get().use(helper.getBlockState(pos), helper.getLevel(), absolute, player, InteractionHand.MAIN_HAND, hit) == InteractionResult.FAIL
                && helper.getBlockState(pos).getValue(BuilderBlock.FACING) == Direction.NORTH, "the wrench must not turn a builder with something to build");
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, ItemStack.EMPTY);
        require(helper, builder.getBuilder() == null && helper.getBlockState(pos).getValue(BuilderBlock.SNAPSHOT_TYPE) == EnumOptionalSnapshotType.NONE,
                "taking the snapshot out empties the slot");
        require(helper, BCBuilders.BUILDER.get().use(helper.getBlockState(pos), helper.getLevel(), absolute, player, InteractionHand.MAIN_HAND, hit).consumesAction()
                && helper.getBlockState(pos).getValue(BuilderBlock.FACING) == Direction.EAST, "the wrench turns an empty builder clockwise");
        BuilderBlockEntity turned = tile(helper, pos, BuilderBlockEntity.class);
        turned.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, template.copy());
        turned.setItem(7, new ItemStack(Items.BRICKS, 12));
        require(helper, turned.getRotation() == Rotation.CLOCKWISE_180, "a template facing west in a builder facing east turns half round");
        require(helper, turned.getBasePoses().equals(List.of(absolute.west())) && turned.getBox().min().equals(absolute.west()),
                "a turned builder builds behind its new back, got " + turned.getBasePoses());

        CompoundTag saved = turned.saveWithoutMetadata();
        BuilderBlockEntity loaded = new BuilderBlockEntity(absolute, helper.getBlockState(pos));
        loaded.load(saved);
        require(helper, loaded.getItem(7).is(Items.BRICKS) && loaded.getItem(7).getCount() == 12
                && ItemStack.matches(loaded.getItem(BuilderBlockEntity.SLOT_SNAPSHOT), template), "saving must keep the items");
        require(helper, loaded.getTanks().get(2).contents().equals(new FluidAmount(Fluids.LAVA, 500)), "saving must keep the tanks");
        require(helper, loaded.getBuilder() == loaded.templateBuilder && loaded.getRotation() == Rotation.CLOCKWISE_180
                && loaded.getBox().min().equals(turned.getBox().min()) && loaded.getBox().max().equals(turned.getBox().max()),
                "loading must reload the snapshot from its item, with its rotation and area");
        require(helper, loaded.getBasePoses().equals(turned.getBasePoses()) && loaded.getCurrentBasePosIndex() == turned.getCurrentBasePosIndex(),
                "saving must keep where the builder builds");
        CompoundTag update = turned.getUpdateTag();
        require(helper, update.contains("box") && update.getString("snapshotType").equals("TEMPLATE"), "clients must receive the area and snapshot type");

        helper.setBlock(pos, Blocks.AIR);
        require(helper, dropped(helper, pos, template) && dropped(helper, pos, new ItemStack(Items.BRICKS, 12)),
                "a broken builder drops its snapshot and resources");
        helper.succeed();
    }
}
