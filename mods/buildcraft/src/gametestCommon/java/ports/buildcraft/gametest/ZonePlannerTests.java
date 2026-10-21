package ports.buildcraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.MapLocationItem;
import ports.buildcraft.core.MarkerPathBlockEntity;
import ports.buildcraft.core.MarkerVolumeBlockEntity;
import ports.buildcraft.core.PaintbrushItem;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.robotics.BCRobotics;
import ports.buildcraft.robotics.ZonePackets;
import ports.buildcraft.robotics.ZonePlannerBlockEntity;
import ports.buildcraft.robotics.ZonePlannerMenu;
import ports.buildcraft.robotics.zone.ZoneChunk;
import ports.buildcraft.robotics.zone.ZonePlan;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;

/** The zone planner, the map locations it reads and the paintbrushes that choose a layer. */
public final class ZonePlannerTests {
    private ZonePlannerTests() {}

    /**
     * A plan stores columns per chunk, paints a half-open rectangle, and keeps upstream's off-by-one full-chunk clear
     * and its {@code << 4 + 8} distance. Copying keeps a filled chunk, and reading replaces what was there.
     */
    public static void zonePlanStoresColumns(GameTestHelper helper) {
        ZoneChunk filled = new ZoneChunk();
        for (int i = 0; i < 256; i++) filled.set(i & 15, i >> 4, true);
        BCTests.require(helper, filled.get(15, 15), "filling every column marks the chunk full");
        filled.set(0, 0, false);
        BCTests.require(helper, !filled.get(0, 0) && !filled.get(15, 15) && filled.get(1, 0),
                "clearing one column of a full chunk also clears column (15, 15)");

        ZonePlan plan = new ZonePlan();
        for (int i = 0; i < 256; i++) plan.set(i & 15, i >> 4, true);
        ZonePlan copy = new ZonePlan(plan);
        BCTests.require(helper, copy.get(0, 0) && copy.get(15, 15), "copying a plan keeps a completely filled chunk");
        plan.set(16, 0, true);
        ZonePlan distant = new ZonePlan();
        distant.set(16, 0, true);
        double distance = distant.distanceToSquared(BlockPos.ZERO);
        BCTests.require(helper, distance == 4096.0 * 4096.0, "chunk 1 is 4096 blocks away under upstream's shift, got " + distance);

        ZonePlan painted = new ZonePlan();
        painted.paintRectangle(0, 0, 0, 0, true);
        BCTests.require(helper, !painted.get(0, 0), "a press that has not moved to another column paints nothing");
        painted.paintRectangle(0, 0, 2, 2, true);
        BCTests.require(helper, painted.get(0, 0) && painted.get(1, 1) && !painted.get(2, 2), "the painted rectangle is half-open");

        long originColumns = plan.columns().stream().filter(column -> column.getX() == 0 && column.getZ() == 0).count();
        BCTests.require(helper, originColumns == 2, "the extra 0..15 pass lists a column in chunk 0 twice, got " + originColumns);

        CompoundTag tag = new CompoundTag();
        plan.write(tag);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        plan.write(buffer);
        ZonePlan read = new ZonePlan();
        read.set(100, 100, true);
        read.read(tag);
        BCTests.require(helper, read.get(0, 0) && read.get(16, 0) && !read.get(100, 100), "reading NBT replaces the plan");
        ZonePlan moved = read.offset(3, -2);
        BCTests.require(helper, moved.get(3, -2) && !moved.get(0, 0), "offset moves every column");
        ZonePlan fromBuffer = new ZonePlan();
        fromBuffer.read(buffer);
        BCTests.require(helper, fromBuffer.get(16, 0) && !fromBuffer.get(100, 100), "a plan survives its byte buffer");
        helper.succeed();
    }

    /**
     * A clean map records a spot, a land mark's area or a path, one from a stack, and sneaking in the air clears it.
     * A recorded map refuses another block. A clean stack holds 16 and a recorded one holds one.
     */
    public static void mapLocationRecordsAndCleans(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "maps"), null);
        BlockPos spot = new BlockPos(2, 1, 2);
        helper.setBlock(spot, Blocks.STONE);
        ItemStack stack = new ItemStack(BCCore.MAP_LOCATION.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BCTests.require(helper, stack.getMaxStackSize() == 16, "a clean map stacks to 16");
        BCTests.require(helper, MapLocationItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, spot, Direction.NORTH)) == InteractionResult.SUCCESS,
                "using a clean map on stone records it");
        BCTests.require(helper, MapLocationItem.Type.of(stack) == MapLocationItem.Type.SPOT, "stone is a spot");
        CompoundTag spotTag = stack.getTag();
        BlockPos absolute = helper.absolutePos(spot);
        BCTests.require(helper, spotTag != null && spotTag.getInt("x") == absolute.getX() && spotTag.getByte("side") == Direction.NORTH.get3DDataValue(),
                "the spot keeps the block and the face");
        BCTests.require(helper, stack.getMaxStackSize() == 1, "a recorded map does not stack");
        BCTests.require(helper, MapLocationItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, spot, Direction.UP)) == InteractionResult.FAIL,
                "a recorded map refuses another block");

        player.setShiftKeyDown(true);
        player.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        BCTests.require(helper, MapLocationItem.Type.of(player.getItemInHand(InteractionHand.MAIN_HAND)) == MapLocationItem.Type.CLEAN
                && player.getItemInHand(InteractionHand.MAIN_HAND).getTag() == null, "sneaking in the air clears a map");
        player.setShiftKeyDown(false);

        BlockPos mark = new BlockPos(4, 1, 2);
        helper.setBlock(mark, BCCore.MARKER_VOLUME.get().defaultBlockState());
        MarkerVolumeBlockEntity volume = BCTests.tile(helper, mark, MarkerVolumeBlockEntity.class);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCore.MAP_LOCATION.get()));
        MapLocationItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, mark, Direction.UP));
        ItemStack area = player.getItemInHand(InteractionHand.MAIN_HAND);
        BCTests.require(helper, MapLocationItem.Type.of(area) == MapLocationItem.Type.AREA
                && area.getTag().getInt("xMin") == volume.min().getX() && area.getTag().getInt("xMax") == volume.max().getX(),
                "a land mark records its area");

        BlockPos pathA = new BlockPos(1, 1, 5);
        BlockPos pathB = new BlockPos(4, 1, 5);
        helper.setBlock(pathA, BCCore.MARKER_PATH.get().defaultBlockState());
        helper.setBlock(pathB, BCCore.MARKER_PATH.get().defaultBlockState());
        MarkerCaches.PATH.getSubCache(helper.getLevel()).tryConnect(helper.absolutePos(pathA), helper.absolutePos(pathB));
        MarkerPathBlockEntity path = BCTests.tile(helper, pathA, MarkerPathBlockEntity.class);
        ItemStack stacked = new ItemStack(BCCore.MAP_LOCATION.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stacked);
        MapLocationItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, pathA, Direction.UP));
        BCTests.require(helper, stacked.getCount() == 3 && MapLocationItem.Type.of(stacked) == MapLocationItem.Type.CLEAN,
                "one map comes off the stack and the rest stay clean");
        ItemStack recorded = player.getInventory().items.stream().filter(item -> MapLocationItem.Type.of(item) == MapLocationItem.Type.PATH).findFirst().orElse(ItemStack.EMPTY);
        BCTests.require(helper, recorded.getCount() == 1 && MapLocationItem.path(recorded) != null && MapLocationItem.path(recorded).equals(path.getPath()),
                "the recorded map is the path, got " + MapLocationItem.path(recorded));
        helper.succeed();
    }

    /**
     * Importing a zone takes 201 ticks and stores it relative to the planner; exporting writes it back in world
     * coordinates. A clean brush never starts, and does not throw.
     */
    public static void zonePlannerImportsAndExports(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCRobotics.ZONE_PLANNER.get().defaultBlockState());
        ZonePlannerBlockEntity planner = BCTests.tile(helper, pos, ZonePlannerBlockEntity.class);
        BlockPos column = helper.absolutePos(pos).offset(3, 0, -2);
        ItemStack map = new ItemStack(BCCore.MAP_LOCATION.get());
        ZonePlan world = new ZonePlan();
        world.set(column.getX(), column.getZ(), true);
        MapLocationItem.setZone(map, world);
        planner.setItem(ZonePlannerBlockEntity.SLOT_INPUT_BRUSH, new ItemStack(BCCore.PAINTBRUSHES.get(DyeColor.RED).get()));
        planner.setItem(ZonePlannerBlockEntity.SLOT_INPUT_MAP, map);
        planner.serverTick();
        BCTests.require(helper, !planner.hasPendingClientUpdate()
                && planner.getUpdateTag().getCompound("deltaInput").getList("changing", 10).size() == 2,
                "the first import tick sends the progress animation");
        for (int tick = 0; tick < 199; tick++) planner.serverTick();
        BCTests.require(helper, planner.getItem(ZonePlannerBlockEntity.SLOT_INPUT_MAP).is(BCCore.MAP_LOCATION.get())
                && !planner.layers[DyeColor.RED.getId()].get(3, -2), "the import is still running after 200 ticks");
        planner.serverTick();
        BCTests.require(helper, planner.getItem(ZonePlannerBlockEntity.SLOT_INPUT_MAP).isEmpty()
                && planner.getItem(ZonePlannerBlockEntity.SLOT_INPUT_RESULT).is(BCCore.MAP_LOCATION.get())
                && MapLocationItem.Type.of(planner.getItem(ZonePlannerBlockEntity.SLOT_INPUT_RESULT)) == MapLocationItem.Type.CLEAN
                && planner.layers[DyeColor.RED.getId()].get(3, -2), "the 201st tick stores the zone relative to the planner and returns a blank map");

        planner.setItem(ZonePlannerBlockEntity.SLOT_OUTPUT_BRUSH, new ItemStack(BCCore.PAINTBRUSHES.get(DyeColor.RED).get()));
        planner.setItem(ZonePlannerBlockEntity.SLOT_OUTPUT_MAP, new ItemStack(BCCore.MAP_LOCATION.get()));
        // The import's idle ticks parked the output at -1, so the first tick only climbs back to 0.
        for (int tick = 0; tick < 202; tick++) planner.serverTick();
        ItemStack exported = planner.getItem(ZonePlannerBlockEntity.SLOT_OUTPUT_RESULT);
        ZonePlan exportedPlan = MapLocationItem.zone(exported);
        BCTests.require(helper, planner.getItem(ZonePlannerBlockEntity.SLOT_OUTPUT_MAP).isEmpty() && exportedPlan != null && exportedPlan.get(column.getX(), column.getZ())
                && MapLocationItem.Type.of(exported) == MapLocationItem.Type.ZONE, "exporting writes the layer back in world coordinates");

        planner.setItem(ZonePlannerBlockEntity.SLOT_INPUT_BRUSH, new ItemStack(BCCore.PAINTBRUSH.get()));
        planner.setItem(ZonePlannerBlockEntity.SLOT_INPUT_MAP, map.copy());
        planner.setItem(ZonePlannerBlockEntity.SLOT_INPUT_RESULT, ItemStack.EMPTY);
        for (int tick = 0; tick < 5; tick++) planner.serverTick();
        BCTests.require(helper, planner.getItem(ZonePlannerBlockEntity.SLOT_INPUT_MAP).is(BCCore.MAP_LOCATION.get())
                && planner.getItem(ZonePlannerBlockEntity.SLOT_INPUT_RESULT).isEmpty(), "a clean brush does not import");

        CompoundTag saved = planner.saveWithoutMetadata();
        ZonePlannerBlockEntity loaded = new ZonePlannerBlockEntity(helper.absolutePos(pos), planner.getBlockState());
        loaded.load(saved);
        BCTests.require(helper, loaded.layers[DyeColor.RED.getId()].get(3, -2) && loaded.getItem(ZonePlannerBlockEntity.SLOT_OUTPUT_RESULT).is(BCCore.MAP_LOCATION.get()),
                "the layers and slots survive a save");
        helper.succeed();
    }

    /**
     * The menu takes brushes and one recorded map, the outputs take nothing, and a hopper cannot insert. Breaking
     * the planner drops its slots. A plan packet applies only while its GUI is open.
     */
    public static void zonePlannerMenuRejectsAndDrops(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, BCRobotics.ZONE_PLANNER.get().defaultBlockState());
        ZonePlannerBlockEntity planner = BCTests.tile(helper, pos, ZonePlannerBlockEntity.class);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "planner"), null);
        BlockPos absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        ZonePlannerMenu menu = new ZonePlannerMenu(0, player.getInventory(), planner);
        int storage = 36;
        int inputBrush = 36 + ZonePlannerBlockEntity.SLOT_INPUT_BRUSH;
        int inputMap = 36 + ZonePlannerBlockEntity.SLOT_INPUT_MAP;
        int inputResult = 36 + ZonePlannerBlockEntity.SLOT_INPUT_RESULT;
        ItemStack brush = new ItemStack(BCCore.PAINTBRUSHES.get(DyeColor.BLUE).get());
        ItemStack blank = new ItemStack(BCCore.MAP_LOCATION.get());
        ItemStack zone = blank.copy();
        MapLocationItem.setZone(zone, new ZonePlan());
        BCTests.require(helper, menu.getSlot(storage).mayPlace(brush) && !menu.getSlot(storage).mayPlace(blank), "the brush grid takes only paintbrushes");
        BCTests.require(helper, menu.getSlot(inputMap).mayPlace(zone) && !menu.getSlot(inputMap).mayPlace(blank)
                && !menu.getSlot(inputMap).mayPlace(new ItemStack(BCCore.MAP_LOCATION.get(), 2)), "the input map must be one stack that holds a zone");
        BCTests.require(helper, !menu.getSlot(inputResult).mayPlace(blank) && !menu.getSlot(inputResult).mayPlace(brush), "the result slots take nothing");

        helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState());
        HopperBlockEntity hopper = BCTests.tile(helper, pos.above(), HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.DIRT));
        BlockState hopperState = helper.getBlockState(pos.above());
        BlockEntityTicker<HopperBlockEntity> ticker = ((EntityBlock) Blocks.HOPPER).getTicker(helper.getLevel(), hopperState, BlockEntityType.HOPPER);
        BCTests.require(helper, ticker != null, "the hopper has a ticker");
        BlockPos hopperPos = helper.absolutePos(pos.above());
        for (int tick = 0; tick < 30; tick++) ticker.tick(helper.getLevel(), hopperPos, hopperState, hopper);
        BCTests.require(helper, hopper.getItem(0).is(Items.DIRT) && planner.getItem(0).isEmpty(), "a hopper cannot insert into the planner");

        planner.setItem(0, brush.copy());
        helper.setBlock(pos, Blocks.AIR);
        BCTests.require(helper, BuilderTests.dropped(helper, pos, brush), "breaking the planner drops its brushes");

        helper.setBlock(pos, BCRobotics.ZONE_PLANNER.get().defaultBlockState());
        planner = BCTests.tile(helper, pos, ZonePlannerBlockEntity.class);
        menu = new ZonePlannerMenu(1, player.getInventory(), planner);
        player.containerMenu = menu;
        ZonePlan sent = new ZonePlan();
        sent.set(4, 5, true);
        ZonePackets.receivePlan(player, planPacket(absolute, DyeColor.LIME.getId(), sent));
        BCTests.require(helper, planner.layers[DyeColor.LIME.getId()].get(4, 5), "a plan packet applies to the open planner");
        player.containerMenu = new AbstractContainerMenu(null, 2) {
            @Override public boolean stillValid(Player user) { return false; }
            @Override public ItemStack quickMoveStack(Player user, int index) { return ItemStack.EMPTY; }
        };
        ZonePlan other = new ZonePlan();
        other.set(6, 7, true);
        ZonePackets.receivePlan(player, planPacket(absolute, DyeColor.LIME.getId(), other));
        BCTests.require(helper, planner.layers[DyeColor.LIME.getId()].get(4, 5) && !planner.layers[DyeColor.LIME.getId()].get(6, 7),
                "a plan packet for a closed GUI is ignored");
        helper.succeed();
    }

    /**
     * The map slice reports the highest coloured block in the column, which is the emerald when the column above it
     * is air and whatever the world generated when it is not. The ground search stops at y 255.
     */
    public static void zonePlannerMapColoursTheTopBlock(GameTestHelper helper) {
        BlockPos relative = new BlockPos(4, 2, 4);
        helper.setBlock(relative, Blocks.EMERALD_BLOCK);
        BlockPos emerald = helper.absolutePos(relative);
        int emeraldColour = helper.getBlockState(relative).getMapColor(helper.getLevel(), emerald).col;
        BCTests.require(helper, emeraldColour != 0, "emerald has a map colour");
        int slice = Math.floorDiv(emerald.getY(), ZonePlannerMapChunkKey.LEVEL_HEIGHT) + 1;
        int top = slice * ZonePlannerMapChunkKey.LEVEL_HEIGHT;
        int expectedY = Integer.MIN_VALUE;
        int expectedColour = 0;
        for (int y = top; y > helper.getLevel().getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(emerald.getX(), y, emerald.getZ());
            int colour = helper.getLevel().getBlockState(pos).getMapColor(helper.getLevel(), pos).col;
            if (colour != 0) {
                expectedY = y;
                expectedColour = colour;
                break;
            }
        }
        ZonePlannerMapChunk chunk = new ZonePlannerMapChunk(helper.getLevel(),
                new ZonePlannerMapChunkKey(new ChunkPos(emerald), ZonePlannerMapChunkKey.dimensionId(helper.getLevel()), slice));
        ZonePlannerMapChunk.Column column = chunk.column(emerald.getX(), emerald.getZ());
        BCTests.require(helper, column != null && column.y() == expectedY && column.colour() == expectedColour && column.y() >= emerald.getY(),
                "the slice's top coloured block matches the world, got " + column + " expected y " + expectedY);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        chunk.write(buffer);
        ZonePlannerMapChunk echoed = new ZonePlannerMapChunk(buffer);
        ZonePlannerMapChunk.Column echoedColumn = echoed.column(emerald.getX(), emerald.getZ());
        BCTests.require(helper, !buffer.isReadable() && echoedColumn != null
                && echoedColumn.y() == column.y() && echoedColumn.colour() == column.colour(),
                "the scanned column survives the map packet, got " + echoedColumn);
        ZonePlannerMapChunk low = new ZonePlannerMapChunk();
        low.setColumn(0, 0, 0, 111);
        low.setColumn(1, 0, -40, 222);
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        low.write(buffer);
        ZonePlannerMapChunk lowRead = new ZonePlannerMapChunk(buffer);
        ZonePlannerMapChunk.Column atZero = lowRead.column(0, 0);
        ZonePlannerMapChunk.Column below = lowRead.column(1, 0);
        BCTests.require(helper, !buffer.isReadable() && atZero != null && atZero.y() == 0 && atZero.colour() == 111
                && below != null && below.y() == -40 && below.colour() == 222 && lowRead.column(2, 0) == null,
                "a column at y 0 and one below it keep the following columns aligned");
        BlockPos cap = new BlockPos(emerald.getX(), ZonePlannerMapChunkKey.SEARCH_TOP, emerald.getZ());
        BCTests.require(helper, ZonePlannerMapChunkKey.levelOf(helper.getLevel(), cap) == 255 / ZonePlannerMapChunkKey.LEVEL_HEIGHT,
                "the ground search stops at y 255");
        helper.succeed();
    }

    /** The clean paintbrush, a dyed one and the zone planner have their recipes. */
    public static void paintbrushAndZonePlannerRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        CraftingRecipe brush = (CraftingRecipe) recipes.byKey(BuildCraft.id("paintbrush")).orElse(null);
        BCTests.require(helper, brush != null && brush.getResultItem().is(BCCore.PAINTBRUSH.get()), "the paintbrush recipe makes a clean brush");
        CraftingContainer grid = grid();
        grid.setItem(0, new ItemStack(BCCore.PAINTBRUSH.get()));
        grid.setItem(1, new ItemStack(Items.RED_DYE));
        CraftingRecipe dyed = (CraftingRecipe) recipes.byKey(BuildCraft.id("paintbrush_red")).orElse(null);
        BCTests.require(helper, dyed != null && dyed.matches(grid, helper.getLevel()) && dyed.assemble(grid).is(BCCore.PAINTBRUSHES.get(DyeColor.RED).get()),
                "a clean brush and a red dye make a red brush");
        CraftingRecipe planner = (CraftingRecipe) recipes.byKey(BuildCraft.id("zone_planner")).orElse(null);
        BCTests.require(helper, planner != null && planner.getResultItem().is(BCRobotics.ZONE_PLANNER_ITEM.get()), "the zone planner recipe makes a zone planner");
        BCTests.require(helper, PaintbrushItem.colorOf(new ItemStack(BCCore.PAINTBRUSHES.get(DyeColor.RED).get())) == DyeColor.RED
                && PaintbrushItem.colorOf(new ItemStack(BCCore.PAINTBRUSH.get())) == null, "a coloured brush knows its dye and a clean one has none");
        helper.succeed();
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
        BlockPos absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false);
    }

    private static FriendlyByteBuf planPacket(BlockPos pos, int layer, ZonePlan plan) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(pos);
        buffer.writeShort(layer);
        plan.write(buffer);
        return buffer;
    }

    private static CraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        };
        return new CraftingContainer(menu, 3, 3);
    }
}
