package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;
import net.mystcraft.minecraft.writing.WritingDeskBlock;
import net.mystcraft.minecraft.writing.WritingDeskMenu;

/** Runs only in the opt-in smoke mod against real server block entities and menus. */
public final class WritingDeskSmoke {
    private WritingDeskSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        require(server.isSameThread(), "Writing desk tests must run on server thread");
        List<String> passed = new ArrayList<>();
        BlockPos pos = new BlockPos(100020, 80, 100010);
        BlockPos footPos = pos.north();
        BlockState previousFoot = level.getBlockState(footPos);
        BlockEntity footEntity = level.getBlockEntity(footPos);
        CompoundTag previousFootData = footEntity == null ? null : footEntity.saveWithFullMetadata();
        BlockState previous = level.getBlockState(pos);
        BlockEntity previousEntity = level.getBlockEntity(pos);
        CompoundTag previousData = previousEntity == null ? null : previousEntity.saveWithFullMetadata();
        try {
            BlockState state = WritingDeskRegistry.WRITING_DESK.get().defaultBlockState();
            level.setBlock(footPos, state.setValue(WritingDeskBlock.FOOT, true), 2);
            require(level.setBlock(pos, state, 3), "Could not place writing desk");
            require(level.getBlockEntity(footPos) == null, "Secondary desk half created a second inventory");
            require(level.getBlockEntity(pos) instanceof WritingDeskBlockEntity, "Writing desk BE absent");
            WritingDeskBlockEntity desk = (WritingDeskBlockEntity) level.getBlockEntity(pos);
            ItemStack original = page("NormalLighting", "original");
            ItemStack snapshot = original.copy();
            desk.inventory.setItem(WritingDeskBlockEntity.LIBRARY_START, original);
            desk.inventory.setItem(WritingDeskBlockEntity.PAPER, new ItemStack(Items.PAPER, 21));
            desk.inventory.setItem(WritingDeskBlockEntity.INK_VIAL, new ItemStack(InkMixerRegistry.INK_VIAL.get()));
            tick(level, pos, state, desk);
            require(desk.ink() == 1000, "Vial did not provide original 1000 mB volume");
            for (int copy = 0; copy < 20; copy++) require(desk.copyPage(0, 0), "Copy failed before twentieth page: " + copy);
            require(!desk.copyPage(0, 0), "Twenty-first copy succeeded without ink");
            require(desk.ink() == 0 && desk.inventory.getItem(1).getCount() == 1, "Twenty copies did not consume exactly 1000 mB and twenty paper");
            require(desk.inventory.getItem(4).getCount() == 20, "Incorrect paid output count");
            require(desk.inventory.getItem(2).isEmpty() && desk.inventory.getItem(3).getCount() == 1, "Vial was not exchanged for exactly one empty bottle");
            for (int i = 0; i < 10; i++) tick(level, pos, state, desk);
            require(desk.inventory.getItem(3).getCount() == 1, "Idle desk duplicated bottles");
            require(ItemStack.matches(snapshot, original), "Copying mutated or consumed original source");
            require(desk.inventory.getItem(4).getTag().equals(snapshot.getTag()), "Copy lost nested source NBT");
            passed.add("writing desk: one 1000 mB vial pays for exactly twenty 50 mB copies, twenty paper and one bottle; source and nested NBT preserved");

            reset(desk);
            desk.inventory.setItem(5, snapshot.copy());
            desk.inventory.setItem(1, new ItemStack(Items.PAPER, 3));
            desk.inventory.setItem(2, new ItemStack(InkMixerRegistry.INK_VIAL.get()));
            desk.inventory.setItem(3, new ItemStack(Items.GLASS_BOTTLE, 64));
            tick(level, pos, state, desk);
            require(desk.ink() == 0 && desk.inventory.getItem(2).getCount() == 1, "Full bottle shelf consumed vial");
            desk.inventory.setItem(3, ItemStack.EMPTY);
            tick(level, pos, state, desk);
            ItemStack full = snapshot.copy(); full.setCount(64); desk.inventory.setItem(4, full);
            require(!desk.copyPage(0, 0), "Full copy output accepted another page");
            require(desk.ink() == 1000 && desk.inventory.getItem(1).getCount() == 3, "Full copy output spent ink or paper");
            desk.inventory.setItem(4, new ItemStack(Items.STONE));
            require(!desk.copyPage(0, 0) && desk.ink() == 1000 && desk.inventory.getItem(1).getCount() == 3, "Mismatched output spent resources");
            require(!desk.copyPage(-1, 0) && !desk.copyPage(25, 0) && !desk.copyPage(0, 99), "Out-of-range source accepted");
            passed.add("writing desk: full bottle shelf and full or mismatched copy output preserve every input and paid ink charge");

            desk.inventory.setItem(4, ItemStack.EMPTY);
            require(desk.copyPage(0, 0), "Could not spend restored copy charge");
            CompoundTag saved = ((BlockEntity) desk).saveWithFullMetadata();
            WritingDeskBlockEntity loaded = new WritingDeskBlockEntity(pos, state);
            ((BlockEntity) loaded).load(saved);
            require(loaded.ink() == 950, "Fractional desk ink did not survive NBT round trip");
            for (int i = 0; i < WritingDeskBlockEntity.SIZE; i++)
                require(ItemStack.matches(desk.inventory.getItem(i), loaded.inventory.getItem(i)), "Desk inventory NBT changed at " + i);
            passed.add("writing desk: fractional ink and all thirty inventories survive NBT round trip");

            ItemStack libraryFolder = new ItemStack(WritingDeskRegistry.FOLDER.get());
            PageCollectionItem.setPages(libraryFolder, List.of(PageData.payload(page("Flat", "other")), PageData.payload(snapshot)));
            ItemStack librarySnapshot = libraryFolder.copy();
            desk.inventory.setItem(5, libraryFolder);
            require(desk.copyPage(0, 1), "Selected owned folder page did not copy");
            require(ItemStack.matches(libraryFolder, librarySnapshot), "Copying consumed or mutated library folder");
            require(desk.inventory.getItem(4).getCount() == 2 && desk.inventory.getItem(4).getTag().equals(snapshot.getTag()), "Folder page selection copied wrong entry or lost NBT");
            require(desk.ink() == 900 && desk.inventory.getItem(1).getCount() == 1, "Folder page copy cost differed from loose-page cost");
            passed.add("writing desk: selected pages copy from an owned library folder with the same cost, without mutating the folder");

            menuChecks(server, level, pos, desk, passed);
            workspaceChecks(server, level, pos, desk, passed);
        } finally {
            if (level.getBlockEntity(pos) instanceof WritingDeskBlockEntity desk) desk.inventory.clearContent();
            level.setBlock(pos, previous, 2);
            level.setBlock(footPos, previousFoot, 2);
            if (previousFootData != null && level.getBlockEntity(footPos) != null) level.getBlockEntity(footPos).load(previousFootData);
            if (previousData != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(previousData);
        }
        return passed;
    }
    private static void menuChecks(MinecraftServer server, ServerLevel level, BlockPos pos, WritingDeskBlockEntity desk, List<String> passed) {
        reset(desk);
        ServerPlayer first = player(server, level, pos, "DeskSmokeOne", "6d1cf18b-a43e-4354-a16c-d75d097ed101");
        ServerPlayer second = player(server, level, pos, "DeskSmokeTwo", "6d1cf18b-a43e-4354-a16c-d75d097ed102");
        WritingDeskMenu one = (WritingDeskMenu) ((MenuProvider) desk).createMenu(91, first.getInventory(), first);
        WritingDeskMenu two = (WritingDeskMenu) ((MenuProvider) desk).createMenu(92, second.getInventory(), second);
        AbstractContainerMenu firstMenu = one, secondMenu = two;
        require(firstMenu.stillValid(first) && secondMenu.stillValid(second), "Desk menu inaccessible nearby");
        require(!firstMenu.getSlot(3).mayPlace(new ItemStack(Items.GLASS_BOTTLE)) && !firstMenu.getSlot(4).mayPlace(page("NormalLighting", "test")), "Desk output slots accept insertion");
        ItemStack a = page("NormalLighting", "A"), b = page("Flat", "B");
        ItemStack browse = new ItemStack(WritingDeskRegistry.FOLDER.get());
        PageCollectionItem.setPages(browse, List.of(PageData.payload(a), PageData.payload(b)));
        desk.inventory.setItem(WritingDeskBlockEntity.LIBRARY_START, browse);
        ItemStack browseSnapshot = browse.copy();
        require(firstMenu.clickMenuButton(first, WritingDeskMenu.SELECT_SOURCE_PAGE + 1), "Direct source glyph selection failed");
        require(ItemStack.matches(one.sourcePageStack(), b) && ItemStack.matches(browse, browseSnapshot), "Direct source selection changed owned pages or chose wrong payload");
        require(!firstMenu.clickMenuButton(first, WritingDeskMenu.SELECT_SOURCE_PAGE + 255), "Unavailable source index accepted");
        passed.add("writing desk: glyph browser selects exact owned source index without mutating library and rejects unavailable indices");
        ItemStack folder = new ItemStack(WritingDeskRegistry.FOLDER.get());
        PageCollectionItem.setPages(folder, List.of(PageData.payload(a), PageData.payload(b), PageData.payload(a)));
        desk.inventory.setItem(0, folder);
        require(PageCollectionItem.pages(folder).size() == 3 && PageCollectionItem.pages(folder).get(0).equals(PageCollectionItem.pages(folder).get(2)), "Collection lost duplicate pages");
        List<CompoundTag> overflow = new ArrayList<>();
        for (int i = 0; i <= PageCollectionItem.MAX_PAGES; i++) overflow.add(PageData.payload(a));
        boolean rejectedOverflow = false;
        try { PageCollectionItem.setPages(folder, overflow); } catch (IllegalArgumentException expected) { rejectedOverflow = true; }
        require(rejectedOverflow && PageCollectionItem.pages(folder).size() == 3, "Over-capacity collection overwrote stored pages");
        require(firstMenu.clickMenuButton(first, WritingDeskMenu.MOVE_DOWN), "Folder order-down action failed");
        List<CompoundTag> pages = PageCollectionItem.pages(folder);
        require(pages.get(0).equals(PageData.payload(b)) && pages.get(1).equals(PageData.payload(a)) && pages.get(2).equals(PageData.payload(a)), "Folder reorder changed duplicate payloads");
        require(firstMenu.clickMenuButton(first, WritingDeskMenu.EXTRACT), "Folder extraction failed");
        require(ItemStack.matches(firstMenu.getCarried(), a), "Extracted page lost nested metadata");
        require(PageCollectionItem.pages(folder).size() == 2, "Extracted page remained in folder");
        require(firstMenu.clickMenuButton(first, WritingDeskMenu.INSERT), "Folder cursor insertion failed");
        require(firstMenu.getCarried().isEmpty() && PageCollectionItem.pages(folder).size() == 3, "Insertion duplicated page or did not consume cursor");
        require(PageCollectionItem.pages(folder).equals(pages), "Insert/extract round trip changed exact order or NBT");
        CompoundTag external = PageCollectionItem.pages(folder).get(0); external.putString("SmokeMarker", "changed");
        require(!PageCollectionItem.pages(folder).get(0).equals(external), "Collection getters alias persisted payloads");
        passed.add("writing desk: folder ordering, duplicates, nested metadata, cursor insertion and extraction preserve exactly the owned pages");

        ItemStack portfolio = new ItemStack(WritingDeskRegistry.PORTFOLIO.get());
        PageCollectionItem.setPages(portfolio, List.of(PageData.payload(a), PageData.payload(b)));
        desk.inventory.setItem(0, portfolio);
        require(!firstMenu.clickMenuButton(first, WritingDeskMenu.MOVE_UP), "Unordered portfolio accepted folder reorder action");
        desk.inventory.setItem(4, a.copy());
        firstMenu.quickMoveStack(first, 4);
        require(secondMenu.quickMoveStack(second, 4).isEmpty() && desk.inventory.getItem(4).isEmpty(), "Two viewers duplicated copied page output");
        require(count(first) == 1 && count(second) == 0, "Shared output went to more than one viewer");
        for (int i = 0; i < 36; i++) first.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        desk.inventory.setItem(4, a.copy());
        require(firstMenu.quickMoveStack(first, 4).isEmpty() && desk.inventory.getItem(4).getCount() == 1, "Full player inventory lost copied output");
        ((Entity) first).setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        require(!firstMenu.stillValid(first) && !firstMenu.clickMenuButton(first, WritingDeskMenu.COPY), "Distant desk action accepted");
        passed.add("writing desk: two viewers cannot duplicate output, full inventories preserve stock, portfolios reject ordering and distant actions reject");
    }
    private static void workspaceChecks(MinecraftServer server, ServerLevel level, BlockPos pos, WritingDeskBlockEntity desk, List<String> passed) {
        reset(desk);
        ServerPlayer owner = player(server, level, pos, "DeskWorkspace", "6d1cf18b-a43e-4354-a16c-d75d097ed103");
        WritingDeskMenu typed = (WritingDeskMenu) ((MenuProvider) desk).createMenu(93, owner.getInventory(), owner);
        AbstractContainerMenu menu = typed;
        ItemStack a = page("Flat", "workspace-A"), b = page("Void", "workspace-B");
        ItemStack source = new ItemStack(WritingDeskRegistry.PORTFOLIO.get());
        PageCollectionItem.setPages(source, List.of(PageData.payload(a), PageData.payload(a), PageData.payload(b)));
        ItemStack sourceSnapshot = source.copy(), target = new ItemStack(WritingDeskRegistry.FOLDER.get());
        desk.inventory.setItem(5, source); desk.inventory.setItem(0, target);
        desk.inventory.setItem(1, new ItemStack(Items.PAPER, 3));
        desk.inventory.setItem(2, new ItemStack(InkMixerRegistry.INK_VIAL.get()));
        tick(level, pos, level.getBlockState(pos), desk);
        require(menu.clickMenuButton(owner, WritingDeskMenu.WRITE), "Workspace paid write into folder failed");
        require(PageCollectionItem.pages(target).equals(List.of(PageData.payload(a))) && ItemStack.matches(source, sourceSnapshot)
                && desk.ink() == 950 && desk.inventory.getItem(1).getCount() == 2, "Workspace paid write changed source, payload or cost");
        ItemStack blank = new ItemStack(AgeRegistry.PAGE.get()); blank.getOrCreateTag().putString("BlankOwner", "preserve");
        desk.inventory.setItem(0, blank);
        require(menu.clickMenuButton(owner, WritingDeskMenu.WRITE) && PageData.legacySymbol(blank).equals(PageData.legacySymbol(a))
                && blank.getTag().getString("BlankOwner").equals("preserve") && desk.ink() == 900 && desk.inventory.getItem(1).getCount() == 2,
                "Blank-page writing must retain its extra NBT and consume ink but no extra paper");
        require(!menu.clickMenuButton(owner, WritingDeskMenu.WRITE) && desk.ink() == 900, "Writing a filled loose page overwrote it or consumed ink");
        passed.add("writing workspace: right-click writes paid exact owned page to folder, preserves source, and writes blank page without extra paper or overwriting filled pages");

        CompoundTag panel = new CompoundTag(); PageData.makeLinkPanel(panel);
        CompoundTag blankBookPage = new CompoundTag(); blankBookPage.putString("BlankBookOwner", "retained");
        ItemStack book = net.mystcraft.minecraft.age.AgeBookData.create("Desk book", List.of(panel, blankBookPage));
        desk.inventory.setItem(0, book);
        require(menu.clickMenuButton(owner, WritingDeskMenu.WRITE) && desk.ink() == 850
                && desk.inventory.getItem(1).getCount() == 2
                && PageData.readPages(book.getTag()).get(1).getString("BlankBookOwner").equals("retained")
                && PageData.legacySymbol(PageData.readPages(book.getTag()).get(1)).equals(PageData.legacySymbol(a)),
                "Unvisited book writing must fill its existing blank and pay only ink");
        require(!menu.clickMenuButton(owner, WritingDeskMenu.WRITE) && desk.ink() == 850, "Filled descriptive book was overwritten");
        ItemStack allocated = net.mystcraft.minecraft.age.AgeBookData.create("Frozen", List.of(panel, blankBookPage));
        net.mystcraft.minecraft.age.AgeBookData.assign(allocated, UUID.randomUUID(), 999, 7);
        desk.inventory.setItem(0, allocated);
        ItemStack allocatedSnapshot = allocated.copy();
        require(!menu.clickMenuButton(owner, WritingDeskMenu.WRITE) && ItemStack.matches(allocated, allocatedSnapshot)
                && desk.ink() == 850, "Allocated canonical book accepted page editing");
        owner.containerMenu = menu;
        require(menu.clickMenuButton(owner, WritingDeskMenu.BOOK_BEGIN), "Desk book view bind rejected");
        desk.inventory.setItem(0, allocated.copy());
        require(!menu.clickMenuButton(owner, WritingDeskMenu.BOOK_LINK), "Embedded book accepted identical replacement identity");
        require(menu.clickMenuButton(owner, WritingDeskMenu.BOOK_BEGIN), "Replacement view bind rejected");
        desk.inventory.getItem(0).getOrCreateTag().putBoolean("MutatedAfterView", true);
        require(!menu.clickMenuButton(owner, WritingDeskMenu.BOOK_LINK), "Embedded book accepted stale NBT snapshot");
        passed.add("writing books: unvisited first-blank writes pay ink without extra paper; allocated books cannot rewrite frozen pages; embedded links reject replaced identity and mutated snapshots");

        menu.setCarried(ItemStack.EMPTY);
        require(menu.clickMenuButton(owner, WritingDeskMenu.SOURCE_TAKE_ALL) && menu.getCarried().getCount() == 2
                && PageCollectionItem.pages(source).equals(List.of(PageData.payload(b))), "Grouped portfolio take did not move exactly matching owned duplicates");
        require(menu.clickMenuButton(owner, WritingDeskMenu.SOURCE_INSERT_ONE) && menu.getCarried().getCount() == 1
                && PageCollectionItem.pages(source).size() == 2, "Right-click source insertion did not move one physical page");
        ItemStack other = new ItemStack(WritingDeskRegistry.FOLDER.get()); desk.inventory.setItem(6, other);
        require(menu.clickMenuButton(owner, WritingDeskMenu.TAB_INSERT + 2) && menu.getCarried().isEmpty()
                && PageCollectionItem.pages(other).equals(List.of(PageData.payload(a))), "Tab drop did not move cursor stock to addressed collection");
        passed.add("writing workspace: grouped take, single insertion and addressed tab drop preserve physical duplicates without granting catalog pages");

        desk.inventory.setItem(0, target);
        require(menu.clickMenuButton(owner, WritingDeskMenu.TITLE_BEGIN), "Target rename begin failed");
        for (char c : "My Age Pages".toCharArray()) require(menu.clickMenuButton(owner, WritingDeskMenu.TITLE_CHAR | c), "Target title character rejected");
        require(menu.clickMenuButton(owner, WritingDeskMenu.TITLE_APPLY) && target.getHoverName().getString().equals("My Age Pages")
                && PageCollectionItem.pages(target).equals(List.of(PageData.payload(a))), "Renaming changed page stock or failed to name target");
        require(menu.clickMenuButton(owner, WritingDeskMenu.TITLE_BEGIN), "Stale rename begin failed");
        desk.inventory.setItem(0, target.copy());
        require(!menu.clickMenuButton(owner, WritingDeskMenu.TITLE_APPLY), "Rename accepted an identical replacement target stack");
        require(menu.clickMenuButton(owner, WritingDeskMenu.TAB_NEXT) && typed.firstTab() == 1
                && !menu.getSlot(5).isActive() && menu.getSlot(6).isActive(), "Four-tab viewport did not change active inventory slots");
        passed.add("writing workspace: rename preserves page NBT, rejects stale target identity and four-tab viewport exposes only its current physical slots");
        for(int i=0;i<30;i++) require(menu.clickMenuButton(owner,WritingDeskMenu.TAB_NEXT),"Tab next rejected");
        require(typed.firstTab()==21 && !menu.getSlot(25).isActive() && menu.getSlot(26).isActive()
                && menu.getSlot(29).isActive(),"Last four-tab viewport must be21..24 without a blank trailing tab");
        require(menu.clickMenuButton(owner,24) && typed.sourceSlot()==24 && menu.getSlot(29).isActive(),"Twenty-fifth library slot is unreachable");
        for(int i=0;i<30;i++) require(menu.clickMenuButton(owner,WritingDeskMenu.TAB_PREV),"Tab previous rejected");
        require(typed.firstTab()==0 && menu.getSlot(5).isActive() && !menu.getSlot(9).isActive(),"Tab viewport did not clamp at first four slots");
        passed.add("writing workspace: one-slot tab scrolling reaches the twenty-fifth source and clamps exactly to first0..3 and final21..24 physical slots");
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, BlockPos pos, String name, String uuid) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.fromString(uuid), name), null);
        ((Entity) player).setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        player.getInventory().clearContent();
        return player;
    }
    private static int count(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(AgeRegistry.PAGE.get())) count += stack.getCount();
        }
        return count;
    }
    private static ItemStack page(String symbol, String marker) {
        ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
        PageData.setLegacySymbol(page, symbol);
        CompoundTag nested = new CompoundTag(); nested.putString("Marker", marker); nested.putInt("Payload", 42);
        page.getOrCreateTag().put("SmokeNested", nested);
        return page;
    }
    private static void reset(WritingDeskBlockEntity desk) { ((BlockEntity) desk).load(new CompoundTag()); }
    private static void tick(ServerLevel level, BlockPos pos, BlockState state, WritingDeskBlockEntity desk) { WritingDeskBlockEntity.tick(level, pos, state, desk); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
