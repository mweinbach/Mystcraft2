package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;
import static ports.buildcraft.gametest.BuilderTests.dropped;
import static ports.buildcraft.gametest.BuilderTests.record;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.ElectronicLibraryBlockEntity;
import ports.buildcraft.builders.ElectronicLibraryMenu;
import ports.buildcraft.builders.FacingMachineBlock;
import ports.buildcraft.builders.ReplacerBlockEntity;
import ports.buildcraft.builders.ReplacerMenu;
import ports.buildcraft.builders.SchematicSingleItem;
import ports.buildcraft.builders.SnapshotItem;
import ports.buildcraft.builders.SnapshotTooltip;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotPackets;
import ports.buildcraft.builders.snapshot.SnapshotStorage;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.core.BCCore;

/** Loader-neutral GameTest bodies for the electronic library, the replacer and the single block schematic. */
public final class LibraryTests {
    private static final BlockPos LIBRARY = new BlockPos(4, 2, 4);
    private static final BlockPos REPLACER = new BlockPos(5, 2, 5);
    private static final int MENU_SLOT_DOWN_OUT = 36, MENU_SLOT_DOWN_IN = 37, MENU_SLOT_UP_IN = 38, MENU_SLOT_UP_OUT = 39;

    private LibraryTests() {}

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /** A real server player beside {@code pos}; it has no connection, so nothing may be sent to it. */
    private static ServerPlayer player(GameTestHelper helper, BlockPos pos, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name), null);
        BlockPos absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        return player;
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction side) {
        BlockPos absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute), side, absolute, false);
    }

    private static SchematicBlock schematicAt(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        return SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(helper.getLevel(), absolute, absolute));
    }

    /** Uploads {@code key} from the client store as the GUI does, handing each part to the server; returns the part count. */
    private static int upload(GameTestHelper helper, ServerPlayer player, Snapshot.Key key, int partBytes) {
        AtomicInteger parts = new AtomicInteger();
        ElectronicLibraryBlockEntity.upload(helper.absolutePos(LIBRARY), key, partBytes, payload -> {
            FriendlyByteBuf part = buffer();
            payload.accept(part);
            parts.incrementAndGet();
            ElectronicLibraryBlockEntity.receiveUp(player, part);
        });
        return parts.get();
    }

    /** Sends {@code snapshot} as one upload part, as a client that builds its own parts could. */
    private static void uploadWhole(GameTestHelper helper, ServerPlayer player, Snapshot.Key key, CompoundTag snapshot) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(snapshot, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FriendlyByteBuf part = buffer();
        part.writeBlockPos(helper.absolutePos(LIBRARY));
        part.writeNbt(key.serializeNBT());
        part.writeBoolean(true);
        part.writeByteArray(bytes.toByteArray());
        ElectronicLibraryBlockEntity.receiveUp(player, part);
    }

    /** As upstream, a local library keeps one file per snapshot contents, so an earlier run's copy would shadow this one's header. */
    private static void removeContents(SnapshotStorage storage, Snapshot.Key key) {
        storage.getList().stream().filter(stored -> Arrays.equals(stored.hash, key.hash)).forEach(storage::removeSnapshot);
    }

    /** A template that only the client store holds, filled at random so that no earlier run left it on the server. */
    private static Snapshot.Key clientOnlyTemplate() {
        Template template = new Template();
        template.size = new BlockPos(4, 4, 4);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        Random random = new Random();
        BitSet data = new BitSet(template.getDataSize());
        for (int i = 0; i < template.getDataSize(); i++) data.set(i, random.nextBoolean());
        template.data = data;
        template.computeKey();
        template.key = new Snapshot.Key(template.key, new Snapshot.Header(template.key, new UUID(0, 0), new Date(), "Client only"));
        SnapshotStorage.get(SnapshotStorage.Side.CLIENT).addSnapshot(template);
        return template.key;
    }

    /**
     * A recording put in {@code downIn} comes out of {@code downOut} after 50 ticks, the arrow filling as it
     * goes, and its snapshot reaches the client store keyed with its header. A snapshot selected from the
     * client store is uploaded in parts and stored on the server, and a recording of it replaces the item in
     * {@code upIn}. Uploads count only from a player with this library open, only while {@code upIn} holds
     * an item, and only for snapshots of 1 to 2^24 blocks; selections only for the library that is open.
     */
    public static void libraryCopiesSnapshotsDownAndUp(GameTestHelper helper) {
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.STONE);
        ItemStack recorded = record(helper.getLevel(), EnumSnapshotType.BLUEPRINT, helper.absolutePos(new BlockPos(7, 2, 7)),
                new BlockPos(1, 1, 1), Direction.NORTH, "Library test");
        Snapshot.Header recordedHeader = SnapshotItem.getHeader(recorded);
        helper.setBlock(LIBRARY, BCBuilders.ELECTRONIC_LIBRARY.get().defaultBlockState());
        ElectronicLibraryBlockEntity library = tile(helper, LIBRARY, ElectronicLibraryBlockEntity.class);
        library.setItem(ElectronicLibraryBlockEntity.SLOT_DOWN_IN, recorded.copy());

        helper.runAfterDelay(ElectronicLibraryBlockEntity.TRANSFER_TICKS / 2, () -> {
            double progress = library.deltaProgressDown.getDynamic(helper.getLevel().getGameTime(), 0);
            require(helper, progress > 0 && progress < 1, "the down arrow must be filling part way through a copy, got " + progress);
            require(helper, library.getItem(ElectronicLibraryBlockEntity.SLOT_DOWN_OUT).isEmpty(), "a copy must take 50 ticks");
        });
        helper.runAfterDelay(ElectronicLibraryBlockEntity.TRANSFER_TICKS + 5, () -> copiedDownThenUp(helper, library, recorded, recordedHeader));
    }

    private static void copiedDownThenUp(GameTestHelper helper, ElectronicLibraryBlockEntity library, ItemStack recorded, Snapshot.Header recordedHeader) {
        require(helper, library.getItem(ElectronicLibraryBlockEntity.SLOT_DOWN_IN).isEmpty()
                && ItemStack.matches(library.getItem(ElectronicLibraryBlockEntity.SLOT_DOWN_OUT), recorded),
                "after 50 ticks the recording moves from downIn to downOut");

        SnapshotStorage client = SnapshotStorage.get(SnapshotStorage.Side.CLIENT);
        removeContents(client, recordedHeader.key);
        FriendlyByteBuf down = buffer();
        ElectronicLibraryBlockEntity.writeDown(down, recorded);
        ElectronicLibraryBlockEntity.receiveDown(down);
        Snapshot.Key downloaded = new Snapshot.Key(recordedHeader.key, recordedHeader);
        require(helper, client.getSnapshot(downloaded) instanceof Blueprint && client.getList().contains(downloaded),
                "the copied snapshot must reach the client store under its key and header");
        FriendlyByteBuf blank = buffer();
        ElectronicLibraryBlockEntity.writeDown(blank, new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get()));
        require(helper, blank.readableBytes() == 1 && !blank.readBoolean(), "a blank recording sends no snapshot");

        ServerPlayer player = player(helper, LIBRARY, "librarian");
        ElectronicLibraryMenu menu = new ElectronicLibraryMenu(0, player.getInventory(), library);
        Snapshot.Key uploaded = clientOnlyTemplate();
        FriendlyByteBuf elsewhere = buffer();
        ElectronicLibraryBlockEntity.writeSelect(elsewhere, helper.absolutePos(LIBRARY).above(), uploaded);
        ElectronicLibraryBlockEntity.receiveSelect(player, elsewhere);
        require(helper, library.getSelected() == null, "a selection needs this library's GUI open");
        player.containerMenu = menu;
        elsewhere.readerIndex(0);
        ElectronicLibraryBlockEntity.receiveSelect(player, elsewhere);
        require(helper, library.getSelected() == null, "a selection for another library must be ignored");
        FriendlyByteBuf select = buffer();
        ElectronicLibraryBlockEntity.writeSelect(select, helper.absolutePos(LIBRARY), uploaded);
        ElectronicLibraryBlockEntity.receiveSelect(player, select);
        require(helper, uploaded.equals(library.getSelected()), "the open library must take the selection");
        require(helper, library.getUpdateTag().contains("selected"), "clients must receive the selection");

        SnapshotStorage server = SnapshotStorage.get(SnapshotStorage.Side.SERVER);
        Snapshot.Key stored = new Snapshot.Key(uploaded, (Snapshot.Header) null);
        int parts = upload(helper, player, uploaded, 16);
        require(helper, parts > 1, "a snapshot larger than a part must be uploaded in several parts, got " + parts);
        require(helper, library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT).isEmpty() && server.getSnapshot(stored) == null,
                "an upload with nothing in upIn must not make a recording");

        library.setItem(ElectronicLibraryBlockEntity.SLOT_UP_IN, new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get()));
        player.containerMenu = player.inventoryMenu;
        upload(helper, player, uploaded, 16);
        require(helper, library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT).isEmpty() && server.getSnapshot(stored) == null,
                "an upload from a player without the GUI open must be ignored");

        player.containerMenu = menu;
        CompoundTag uploadedTag = Snapshot.writeToNBT(client.getSnapshot(uploaded));
        for (BlockPos size : List.of(new BlockPos(512, 256, 256), new BlockPos(65536, 65536, 1), new BlockPos(-4, 4, 4))) {
            CompoundTag resized = uploadedTag.copy();
            resized.put("size", NbtUtils.writeBlockPos(size));
            uploadWhole(helper, player, uploaded, resized);
            require(helper, library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT).isEmpty(),
                    "an upload spanning " + size.toShortString() + " must be refused");
        }

        upload(helper, player, uploaded, 16);
        ItemStack out = library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT);
        Snapshot.Header outHeader = SnapshotItem.getHeader(out);
        require(helper, out.is(BCBuilders.TEMPLATE_USED.get()) && library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_IN).isEmpty(),
                "an upload replaces upIn's item with a recording of the uploaded template, got " + out);
        require(helper, outHeader != null && outHeader.equals(uploaded.header) && server.getSnapshot(outHeader.key) instanceof Template,
                "the recording keeps the client's header and the server stores the snapshot under it");
        player.containerMenu = player.inventoryMenu;
        client.removeSnapshot(uploaded);
        client.removeSnapshot(downloaded);
        helper.succeed();
    }

    /** A one block blueprint of what is at {@code pos}, keyed with a header as a client store keeps it. */
    private static Blueprint oneBlockBlueprint(GameTestHelper helper, BlockPos pos, String name) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(schematicAt(helper, pos));
        blueprint.data = new int[] {0};
        blueprint.computeKey();
        blueprint.key = new Snapshot.Key(blueprint.key, new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(), name));
        return blueprint;
    }

    /**
     * Not upstream. Survival players can craft the library, so uploads are not trusted: a blueprint costed as
     * stone but placing bedrock, or holding a command block, is refused outright; a chest with a forged loot
     * table is stored without it, and the recording names the stored (sanitized) contents rather than the hash
     * the client claimed. An honest upload keeps the client's header.
     */
    public static void libraryRefusesForgedUploads(GameTestHelper helper) {
        helper.setBlock(LIBRARY, BCBuilders.ELECTRONIC_LIBRARY.get().defaultBlockState());
        ElectronicLibraryBlockEntity library = tile(helper, LIBRARY, ElectronicLibraryBlockEntity.class);
        ServerPlayer player = player(helper, LIBRARY, "forger");
        player.containerMenu = new ElectronicLibraryMenu(0, player.getInventory(), library);
        SnapshotStorage server = SnapshotStorage.get(SnapshotStorage.Side.SERVER);
        BlockPos at = new BlockPos(7, 2, 7);

        helper.setBlock(at, Blocks.STONE);
        Blueprint stone = oneBlockBlueprint(helper, at, "Forged stone");
        CompoundTag bedrock = Snapshot.writeToNBT(stone);
        bedrock.getList("palette", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").putString("placeBlock", "minecraft:bedrock");
        CompoundTag commandBlock = Snapshot.writeToNBT(stone);
        CompoundTag commandData = commandBlock.getList("palette", Tag.TAG_COMPOUND).getCompound(0).getCompound("data");
        commandData.put("blockState", NbtUtils.writeBlockState(Blocks.COMMAND_BLOCK.defaultBlockState()));
        commandData.putString("placeBlock", "minecraft:command_block");
        for (CompoundTag forged : List.of(bedrock, commandBlock)) {
            library.setItem(ElectronicLibraryBlockEntity.SLOT_UP_IN, new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get()));
            uploadWhole(helper, player, stone.key, forged);
            require(helper, library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT).isEmpty()
                    && !library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_IN).isEmpty(), "a forged upload must be refused: " + forged);
        }

        helper.setBlock(at, Blocks.CHEST);
        Blueprint chest = oneBlockBlueprint(helper, at, "Looted chest " + UUID.randomUUID());
        CompoundTag looted = Snapshot.writeToNBT(chest);
        looted.getList("palette", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("tileNbt")
                .putString("LootTable", "minecraft:chests/end_city_treasure");
        uploadWhole(helper, player, chest.key, looted);
        Snapshot.Header header = SnapshotItem.getHeader(library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT));
        Snapshot stored = header == null ? null : server.getSnapshot(header.key);
        require(helper, stored instanceof Blueprint blueprint && blueprint.palette.get(0).getPreviewTileNbt() != null
                && !blueprint.palette.get(0).getPreviewTileNbt().contains("LootTable"),
                "a chest uploaded with a loot table must be stored without it, got " + stored);
        require(helper, Arrays.equals(header.key.hash, stored.key.hash) && header.name.equals(chest.key.header.name),
                "the recording must name the stored contents and keep the client's name");

        library.setItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT, ItemStack.EMPTY);
        library.setItem(ElectronicLibraryBlockEntity.SLOT_UP_IN, new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get()));
        helper.setBlock(at, Blocks.STONE);
        Blueprint honest = oneBlockBlueprint(helper, at, "Honest stone " + UUID.randomUUID());
        uploadWhole(helper, player, honest.key, Snapshot.writeToNBT(honest));
        Snapshot.Header honestHeader = SnapshotItem.getHeader(library.getItem(ElectronicLibraryBlockEntity.SLOT_UP_OUT));
        require(helper, honest.key.header.equals(honestHeader) && server.getSnapshot(honestHeader.key) instanceof Blueprint,
                "an honest upload must keep the client's header, got " + honestHeader);
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    /**
     * Only recordings go into {@code downIn}, any template or blueprint into {@code upIn}, and nothing into the
     * outputs, one at a time; automation reaches every slot from every side but only takes from the outputs.
     * A wrench turns the library. The slots survive a save, clients get the progress, and breaking the library
     * drops its items.
     */
    public static void libraryMenuSidesSavingAndDrops(GameTestHelper helper) {
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.STONE);
        ItemStack recorded = record(helper, EnumSnapshotType.TEMPLATE, new BlockPos(7, 2, 7), new BlockPos(1, 1, 1), Direction.NORTH);
        ItemStack blank = new ItemStack(BCBuilders.TEMPLATE_CLEAN.get());
        helper.setBlock(LIBRARY, BCBuilders.ELECTRONIC_LIBRARY.get().defaultBlockState());
        ElectronicLibraryBlockEntity library = tile(helper, LIBRARY, ElectronicLibraryBlockEntity.class);
        Player player = helper.makeMockPlayer();
        ElectronicLibraryMenu menu = new ElectronicLibraryMenu(0, player.getInventory(), library);
        require(helper, menu.getSlot(MENU_SLOT_DOWN_IN).mayPlace(recorded) && !menu.getSlot(MENU_SLOT_DOWN_IN).mayPlace(blank)
                && menu.getSlot(MENU_SLOT_UP_IN).mayPlace(blank) && menu.getSlot(MENU_SLOT_UP_IN).mayPlace(recorded)
                && !menu.getSlot(MENU_SLOT_UP_IN).mayPlace(new ItemStack(Items.PAPER))
                && !menu.getSlot(MENU_SLOT_DOWN_OUT).mayPlace(recorded) && !menu.getSlot(MENU_SLOT_UP_OUT).mayPlace(blank)
                && menu.getSlot(MENU_SLOT_UP_IN).getMaxStackSize() == 1, "downIn takes recordings, upIn any template or blueprint, one at a time");
        for (Direction side : Direction.values()) {
            require(helper, library.getSlotsForFace(side).length == 4, "automation reaches every slot from " + side);
        }
        require(helper, library.canPlaceItemThroughFace(ElectronicLibraryBlockEntity.SLOT_DOWN_IN, recorded, Direction.UP)
                && !library.canPlaceItemThroughFace(ElectronicLibraryBlockEntity.SLOT_DOWN_IN, blank, Direction.UP)
                && library.canPlaceItemThroughFace(ElectronicLibraryBlockEntity.SLOT_UP_IN, blank, Direction.UP)
                && !library.canPlaceItemThroughFace(ElectronicLibraryBlockEntity.SLOT_UP_OUT, blank, Direction.UP)
                && library.canTakeItemThroughFace(ElectronicLibraryBlockEntity.SLOT_DOWN_OUT, recorded, Direction.DOWN)
                && library.canTakeItemThroughFace(ElectronicLibraryBlockEntity.SLOT_UP_OUT, recorded, Direction.DOWN)
                && !library.canTakeItemThroughFace(ElectronicLibraryBlockEntity.SLOT_UP_IN, blank, Direction.DOWN),
                "automation fills the inputs and empties the outputs");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCCore.WRENCH.get()));
        BlockState state = helper.getBlockState(LIBRARY);
        require(helper, state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, LIBRARY, Direction.UP)).consumesAction()
                && helper.getBlockState(LIBRARY).getValue(FacingMachineBlock.FACING) == Direction.EAST, "a wrench turns the library clockwise");
        ElectronicLibraryBlockEntity turned = tile(helper, LIBRARY, ElectronicLibraryBlockEntity.class);
        require(helper, turned == library, "turning must keep the library's block entity");

        library.setItem(ElectronicLibraryBlockEntity.SLOT_DOWN_OUT, recorded.copy());
        library.setItem(ElectronicLibraryBlockEntity.SLOT_UP_IN, blank.copy());
        CompoundTag saved = library.saveWithoutMetadata();
        ElectronicLibraryBlockEntity loaded = new ElectronicLibraryBlockEntity(helper.absolutePos(LIBRARY), helper.getBlockState(LIBRARY));
        loaded.load(saved);
        require(helper, ItemStack.matches(loaded.getItem(ElectronicLibraryBlockEntity.SLOT_DOWN_OUT), recorded)
                && ItemStack.matches(loaded.getItem(ElectronicLibraryBlockEntity.SLOT_UP_IN), blank), "saving must keep the slots");
        CompoundTag update = library.getUpdateTag();
        require(helper, update.contains("progressDown") && update.contains("progressUp"), "clients must receive both arrows' progress");

        helper.setBlock(LIBRARY, Blocks.AIR);
        require(helper, dropped(helper, LIBRARY, recorded) && dropped(helper, LIBRARY, blank), "a broken library drops its items");
        helper.succeed();
    }

    /**
     * With a recorded blueprint and two recorded single schematics in, the replacer stores a copy of the
     * blueprint with the first block swapped for the second, keeping every other block, and puts a recording of
     * the copy in place of the blueprint under its name, owned by whoever placed the replacer. The schematics
     * are used up, the original blueprint stays stored, and automation reaches none of the slots.
     */
    public static void replacerSwapsBlueprintBlocks(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.DIRT);
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.GOLD_BLOCK);
        ItemStack blueprint = record(helper.getLevel(), EnumSnapshotType.BLUEPRINT, helper.absolutePos(new BlockPos(1, 2, 1)),
                new BlockPos(2, 1, 1), Direction.NORTH, "Swap");
        SchematicBlock stone = schematicAt(helper, new BlockPos(1, 2, 1));
        SchematicBlock dirt = schematicAt(helper, new BlockPos(2, 2, 1));
        SchematicBlock gold = schematicAt(helper, new BlockPos(1, 2, 2));
        ItemStack from = SchematicSingleItem.getUsed(stone), to = SchematicSingleItem.getUsed(gold);

        Player placer = helper.makeMockPlayer();
        BlockState state = BCBuilders.REPLACER.get().defaultBlockState();
        helper.setBlock(REPLACER, state);
        BCBuilders.REPLACER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(REPLACER), state, placer, ItemStack.EMPTY);
        ReplacerBlockEntity replacer = tile(helper, REPLACER, ReplacerBlockEntity.class);
        require(helper, replacer.getOwner().equals(placer.getUUID()), "the replacer belongs to whoever placed it");

        ReplacerMenu menu = new ReplacerMenu(0, placer.getInventory(), replacer);
        ItemStack template = SnapshotItem.getUsed(EnumSnapshotType.TEMPLATE, SnapshotItem.getHeader(blueprint));
        require(helper, menu.getSlot(36).mayPlace(blueprint) && !menu.getSlot(36).mayPlace(template)
                && !menu.getSlot(36).mayPlace(new ItemStack(BCBuilders.BLUEPRINT_CLEAN.get())) && menu.getSlot(36).getMaxStackSize() == 1,
                "the first slot takes one recorded blueprint");
        require(helper, menu.getSlot(37).mayPlace(from) && menu.getSlot(38).mayPlace(to)
                && !menu.getSlot(37).mayPlace(new ItemStack(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get())) && !menu.getSlot(38).mayPlace(blueprint),
                "the schematic slots take recorded single schematics");
        for (Direction side : Direction.values()) {
            require(helper, replacer.getSlotsForFace(side).length == 0 && !replacer.canPlaceItemThroughFace(ReplacerBlockEntity.SLOT_FROM, from, side),
                    "automation must not reach the replacer from " + side);
        }

        replacer.setItem(ReplacerBlockEntity.SLOT_SNAPSHOT, blueprint.copy());
        replacer.setItem(ReplacerBlockEntity.SLOT_FROM, from.copy());
        helper.runAfterDelay(2, () -> {
            require(helper, ItemStack.matches(replacer.getItem(ReplacerBlockEntity.SLOT_SNAPSHOT), blueprint),
                    "nothing is replaced until both schematics are in");
            replacer.setItem(ReplacerBlockEntity.SLOT_TO, to.copy());
        });
        helper.runAfterDelay(4, () -> {
            ItemStack result = replacer.getItem(ReplacerBlockEntity.SLOT_SNAPSHOT);
            Snapshot.Header original = SnapshotItem.getHeader(blueprint), header = SnapshotItem.getHeader(result);
            require(helper, result.is(BCBuilders.BLUEPRINT_USED.get()) && header != null && header.name.equals("Swap")
                    && header.owner.equals(placer.getUUID()) && !header.key.equals(original.key),
                    "the result is a recording of a new blueprint under the old name, owned by the replacer's owner");
            require(helper, replacer.getItem(ReplacerBlockEntity.SLOT_FROM).isEmpty() && replacer.getItem(ReplacerBlockEntity.SLOT_TO).isEmpty(),
                    "both schematics are used up");
            SnapshotStorage storage = SnapshotStorage.get(SnapshotStorage.Side.SERVER);
            require(helper, storage.getSnapshot(header.key) instanceof Blueprint replaced && replaced.get(0, 0, 0).equals(gold)
                    && replaced.get(1, 0, 0).equals(dirt) && !replaced.palette.contains(stone),
                    "the stored copy has gold where the stone was and keeps the dirt");
            require(helper, storage.getSnapshot(original.key) instanceof Blueprint kept && kept.get(0, 0, 0).equals(stone),
                    "the original blueprint stays stored unchanged");

            CompoundTag saved = replacer.saveWithoutMetadata();
            ReplacerBlockEntity loaded = new ReplacerBlockEntity(helper.absolutePos(REPLACER), helper.getBlockState(REPLACER));
            loaded.load(saved);
            require(helper, loaded.getOwner().equals(placer.getUUID()) && ItemStack.matches(loaded.getItem(ReplacerBlockEntity.SLOT_SNAPSHOT), result),
                    "saving must keep the owner and the slots");
            helper.setBlock(REPLACER, Blocks.AIR);
            require(helper, dropped(helper, REPLACER, result), "a broken replacer drops its items");
            helper.succeed();
        });
    }

    /**
     * A blank single schematic used on a block records it, one blank of a stack at a time, and refuses air; the
     * block's own use does not run, so a chest does not open. A recorded one builds the block on the clicked face
     * from the player's inventory, even in creative, and refuses without the items or where it is already built.
     * It builds in place of grass, which a refused build leaves alone. Sneaking wipes it. Recorded schematics, templates and blueprints show their snapshot in the tooltip, and a
     * snapshot survives the encoding the server sends it in.
     */
    public static void singleSchematicRecordsAndPlaces(GameTestHelper helper) {
        try {
            singleSchematic(helper);
        } catch (InvalidSnapshotException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void singleSchematic(GameTestHelper helper) throws InvalidSnapshotException, IOException {
        BlockPos stonePos = new BlockPos(3, 2, 3), airPos = new BlockPos(3, 2, 5), chestPos = new BlockPos(5, 2, 2);
        helper.setBlock(stonePos, Blocks.STONE);
        helper.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get(), 2));
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, airPos, Direction.UP)) == InteractionResult.FAIL
                && player.getMainHandItem().getCount() == 2, "recording air must fail");
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.UP)) == InteractionResult.SUCCESS,
                "recording stone must succeed");
        int usedSlot = -1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(BCBuilders.SCHEMATIC_SINGLE_USED.get())) usedSlot = slot;
        }
        require(helper, player.getMainHandItem().is(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get()) && player.getMainHandItem().getCount() == 1 && usedSlot >= 0,
                "recording from a stack of blanks uses one and gives the recording back");
        ItemStack used = player.getInventory().getItem(usedSlot);
        player.getInventory().setItem(usedSlot, ItemStack.EMPTY);
        SchematicBlock stone = SchematicSingleItem.getSchematic(used);
        require(helper, stone != null && stone.getPreviewState().is(Blocks.STONE) && used.getMaxStackSize() == 1, "the recording holds the stone");
        SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.UP));
        require(helper, player.getMainHandItem().is(BCBuilders.SCHEMATIC_SINGLE_USED.get()), "the last blank becomes the recording in hand");

        BlockPos placePos = stonePos.above();
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.UP)) == InteractionResult.FAIL
                && helper.getBlockState(placePos).isAir(), "building without the items must fail, even in creative");
        player.getInventory().setItem(5, new ItemStack(Items.STONE));
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.UP)) == InteractionResult.SUCCESS
                && helper.getBlockState(placePos).is(Blocks.STONE) && player.getInventory().getItem(5).isEmpty(),
                "building on the clicked face takes the stone from the inventory");
        player.getInventory().setItem(5, new ItemStack(Items.STONE));
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.WEST)) == InteractionResult.SUCCESS
                && helper.getBlockState(stonePos.west()).is(Blocks.STONE), "building on a side face builds beside the block");
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.WEST)) == InteractionResult.FAIL,
                "a block already built there must not be built again");

        BlockPos grassPos = new BlockPos(1, 2, 5);
        helper.setBlock(grassPos.below(), Blocks.DIRT);
        helper.setBlock(grassPos, Blocks.GRASS);
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, grassPos, Direction.UP)) == InteractionResult.FAIL
                && helper.getBlockState(grassPos).is(Blocks.GRASS), "a build without the items must leave the grass it would replace");
        ItemStack stoneSchematic = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, SchematicSingleItem.getUsed(schematicAt(helper, grassPos)));
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, grassPos, Direction.UP)) == InteractionResult.FAIL
                && helper.getBlockState(grassPos).is(Blocks.GRASS), "a schematic of the grass already there builds nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, stoneSchematic);
        player.getInventory().setItem(5, new ItemStack(Items.STONE));
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, grassPos, Direction.UP)) == InteractionResult.SUCCESS
                && helper.getBlockState(grassPos).is(Blocks.STONE) && player.getInventory().getItem(5).isEmpty(), "a build replaces the grass in place");

        Optional<TooltipComponent> tooltip = player.getMainHandItem().getTooltipImage();
        require(helper, tooltip.isPresent() && tooltip.get() instanceof SnapshotTooltip snapshotTooltip && snapshotTooltip.snapshot() instanceof Blueprint preview
                && preview.size.equals(new BlockPos(1, 1, 1)) && preview.get(0, 0, 0).equals(stone),
                "a recorded schematic's tooltip shows its block as a one-block blueprint");
        require(helper, new ItemStack(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get()).getTooltipImage().isEmpty(), "a blank schematic's tooltip shows nothing");
        ItemStack template = record(helper, EnumSnapshotType.TEMPLATE, stonePos, new BlockPos(1, 2, 1), Direction.NORTH);
        Snapshot.Header header = SnapshotItem.getHeader(template);
        require(helper, template.getTooltipImage().orElse(null) instanceof SnapshotTooltip stored && header.key.equals(stored.key()),
                "a recorded template's tooltip names its snapshot for the client to fetch");

        player.setShiftKeyDown(true);
        require(helper, SchematicSingleItem.useFirst(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit(helper, stonePos, Direction.UP)) == InteractionResult.SUCCESS
                && player.getMainHandItem().is(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get()), "sneaking wipes the recording");
        player.setShiftKeyDown(false);

        ServerPlayer clicker = player(helper, chestPos, "schematic");
        clicker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get()));
        InteractionResult clicked = clicker.gameMode.useItemOn(clicker, helper.getLevel(), clicker.getMainHandItem(), InteractionHand.MAIN_HAND,
                hit(helper, chestPos, Direction.UP));
        SchematicBlock chest = SchematicSingleItem.getSchematic(clicker.getMainHandItem());
        require(helper, clicked.consumesAction() && chest != null && chest.getPreviewState().is(Blocks.CHEST),
                "the loader's block click hook records the chest, got " + clicked);
        require(helper, clicker.containerMenu == clicker.inventoryMenu, "recording a chest must not open it");

        Snapshot snapshot = SnapshotStorage.get(SnapshotStorage.Side.SERVER).getSnapshot(header.key);
        FriendlyByteBuf encoded = buffer();
        SnapshotPackets.write(encoded, snapshot);
        Snapshot decoded = SnapshotPackets.read(encoded);
        require(helper, decoded instanceof Template && decoded.key.equals(snapshot.key) && decoded.size.equals(snapshot.size),
                "a snapshot must survive the encoding the server sends it in");
        FriendlyByteBuf unknown = buffer();
        unknown.writeNbt(new Snapshot.Key().serializeNBT());
        SnapshotPackets.receiveRequest(clicker, unknown);
        helper.succeed();
    }
}
