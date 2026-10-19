package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.mystcraft.integration.network.MenuActions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.BinderRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.binder.BookBinderBlockEntity;
import net.mystcraft.minecraft.binder.BookBinderMenu;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime-only binder assertions: actual menu transactions and all 256 persisted page entries. */
public final class BinderSmoke {
    private BinderSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        CraftingContainer recipeGrid = new CraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        }, 3, 3);
        for (int i = 0; i < 3; i++) recipeGrid.setItem(i, new ItemStack(Items.IRON_INGOT));
        for (int i : new int[] {3, 4, 5, 6, 8}) recipeGrid.setItem(i, new ItemStack(Items.OAK_PLANKS));
        ItemStack craftedBinder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, recipeGrid, level)
                .orElseThrow(() -> new AssertionError("Classic iron/planks binder recipe not loaded")).assemble(recipeGrid);
        require(craftedBinder.is(BinderRegistry.BOOK_BINDER_ITEM.get()) && craftedBinder.getCount() == 1, "Binder recipe output wrong");
        passed.add("binder recipe: loaded classic three iron plus five planks recipe creates one book binder");
        BlockPos pos = new BlockPos(100030, 80, 100030);
        BlockState previous = level.getBlockState(pos);
        BlockEntity previousEntity = level.getBlockEntity(pos);
        CompoundTag previousTag = previousEntity == null ? null : previousEntity.saveWithFullMetadata();
        try {
            BlockState state = BinderRegistry.BOOK_BINDER.get().defaultBlockState();
            level.setBlock(pos, state, 3);
            require(level.getBlockEntity(pos) instanceof BookBinderBlockEntity, "Binder block entity missing");
            BookBinderBlockEntity binder = (BookBinderBlockEntity) level.getBlockEntity(pos);
            ServerPlayer first = new ServerPlayer(server, level, new GameProfile(UUID.fromString("621ec9f9-0f45-4c15-bf75-bb36393bb701"), "BinderSmokeOne"), null);
            ServerPlayer second = new ServerPlayer(server, level, new GameProfile(UUID.fromString("621ec9f9-0f45-4c15-bf75-bb36393bb702"), "BinderSmokeTwo"), null);
            first.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            second.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            AbstractContainerMenu one = new BookBinderMenu(1, first.getInventory(), binder);
            AbstractContainerMenu two = new BookBinderMenu(2, second.getInventory(), binder);
            AbstractContainerMenu previousMenu = first.containerMenu;
            first.containerMenu = one;
            try {
                require(dispatch(first, one.containerId, BookBinderMenu.TITLE_CHAR | '\u03a9'), "Encoded Unicode binder title action refused");
                require(binder.pendingTitle().equals("\u03a9"), "Encoded title command lost high bits");
                require(!dispatch(first, one.containerId + 1, BookBinderMenu.TITLE_CLEAR) && binder.pendingTitle().equals("\u03a9"), "Wrong-container action changed binder title");
                require(dispatch(first, one.containerId, BookBinderMenu.TITLE_CLEAR), "Encoded title reset refused");
                one.setCarried(new ItemStack(Items.PAPER));
                require(dispatch(first, one.containerId, BookBinderMenu.INSERT_ONE) && binder.pageCount() == 1, "Encoded right-click insert action truncated");
                require(dispatch(first, one.containerId, BookBinderMenu.REMOVE_PAGE) && binder.pageCount() == 0 && !one.getCarried().isEmpty(), "Encoded remove-page action truncated");
                one.setCarried(ItemStack.EMPTY);
            } finally { first.containerMenu = previousMenu; }
            passed.add("binder transport: full-width Unicode title, right-click insert and page removal survive byte-buffer encoding/decoding; wrong menu ID cannot mutate state");
            binder.inventory.setItem(0, new ItemStack(Items.LEATHER, 2));
            one.setCarried(new ItemStack(MystcraftItems.LINKING_PANEL.get()));
            require(one.clickMenuButton(first, BookBinderMenu.INSERT_ALL), "Panel insertion refused");
            require(!binder.canBind(), "Untitled binder produced a book");
            for (char ch : "Smoke Ordered".toCharArray()) require(one.clickMenuButton(first, BookBinderMenu.TITLE_CHAR | ch), "Title character refused");
            ItemStack symbol = new ItemStack(AgeRegistry.PAGE.get(), 2);
            PageData.setLegacySymbol(symbol, "BiomeControllerSingle");
            PageData.setQuality(symbol, "smoke", 17);
            symbol.getOrCreateTag().putString("UnknownPayload", "preserve me");
            one.setCarried(symbol);
            one.clickMenuButton(first, BookBinderMenu.INSERT_ALL | 1);
            one.setCarried(new ItemStack(Items.PAPER));
            one.clickMenuButton(first, BookBinderMenu.INSERT_ONE | 3);
            require(binder.pageCount() == 4 && one.getCarried().isEmpty(), "Ordered page insertion lost stock");
            require(binder.canBind(), "Valid titled binder cannot bind");
            one.setCarried(new ItemStack(MystcraftItems.LINKING_PANEL.get()));
            one.clickMenuButton(first, BookBinderMenu.INSERT_ONE | 1);
            require(!binder.canBind(), "Second link panel was accepted for binding");
            one.clickMenuButton(first, BookBinderMenu.REMOVE_PAGE | 1);
            require(one.getCarried().is(MystcraftItems.LINKING_PANEL.get()), "Removing extra panel lost its item");
            one.setCarried(ItemStack.EMPTY);
            require(binder.canBind(), "Removing extra panel failed to restore valid book");
            one.broadcastChanges();
            two.broadcastChanges();
            require(one.getSlot(1).hasItem(), "Valid binder has no result preview");
            require(binder.inventory.getItem(0).getCount() == 2 && binder.pageCount() == 4, "Preview consumed binder ingredients");
            passed.add("binder: title and first-panel validation, ordered insertion, duplicate symbol pages and blank paper conversion; preview consumes nothing");

            first.getInventory().clearContent();
            for (int i = 0; i < 36; i++) first.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            require(one.quickMoveStack(first, 1).isEmpty(), "Full inventory accepted binder output");
            require(binder.inventory.getItem(0).getCount() == 2 && binder.pageCount() == 4, "Failed transfer consumed binder inputs");
            first.getInventory().clearContent();
            require(!one.quickMoveStack(first, 1).isEmpty(), "Valid binder output could not be taken");
            require(two.quickMoveStack(second, 1).isEmpty(), "Second viewer duplicated a descriptive book");
            ItemStack book = first.getInventory().getItem(8);
            if (!book.is(AgeRegistry.DESCRIPTIVE_BOOK.get())) {
                for (int i = 0; i < 36; i++) if (first.getInventory().getItem(i).is(AgeRegistry.DESCRIPTIVE_BOOK.get())) book = first.getInventory().getItem(i);
            }
            require(book.is(AgeRegistry.DESCRIPTIVE_BOOK.get()), "Player did not receive descriptive book");
            List<CompoundTag> pages = PageData.readPages(book.getOrCreateTag());
            require(pages.size() == 4 && PageData.isLinkPanel(pages.get(0)), "Book page order changed");
            require(pages.get(1).equals(pages.get(2)) && pages.get(1).getString("UnknownPayload").equals("preserve me"), "Duplicate page or unknown NBT lost");
            require(pages.get(1).getCompound("Quality").getInt("smoke") == 17 && pages.get(3).isEmpty(), "Quality or blank page changed");
            require(book.getHoverName().getString().equals("Smoke Ordered") && AgeBookData.isUnallocated(book), "Binding changed title or allocated an Age before first link");
            require(binder.inventory.getItem(0).getCount() == 1 && binder.pageCount() == 0 && binder.pendingTitle().isEmpty(), "Binding failed exact one-cover/all-pages/title consumption");
            passed.add("binder: take-to-bind creates exactly one unallocated descriptive book, retains title/order/duplicate/quality/unknown NBT, and cannot duplicate across viewers or consume on full inventory");

            binder.insertPages(new ItemStack(MystcraftItems.LINKING_PANEL.get()), 0, true);
            for (int i = 1; i < BookBinderBlockEntity.MAX_PAGES; i++) {
                ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
                page.getOrCreateTag().putInt("PageIndex", i);
                binder.insertPages(page, i, true);
            }
            for (char ch : "Saved Binder".toCharArray()) binder.appendTitle(ch);
            CompoundTag saved = ((BlockEntity) binder).saveWithFullMetadata();
            BookBinderBlockEntity loaded = new BookBinderBlockEntity(pos, state);
            ((BlockEntity) loaded).load(saved);
            require(loaded.pageCount() == 256 && loaded.pendingTitle().equals("Saved Binder"), "256-page binder or title failed NBT roundtrip");
            require(loaded.inventory.getItem(257).getOrCreateTag().getInt("PageIndex") == 255, "Page slot257 wrapped and corrupted saved cover/output");
            require(loaded.inventory.getItem(0).is(Items.LEATHER) && loaded.inventory.getItem(1).isEmpty(), "High page indices overwrote cover or result");
            ItemStack tooMany = new ItemStack(AgeRegistry.PAGE.get());
            require(loaded.insertPages(tooMany, 256, true) == 0 && tooMany.getCount() == 1, "Page cap discarded an incoming page");
            passed.add("binder: all 256 ordered page entries and title roundtrip NBT without byte-slot wraparound, and page cap preserves rejected input");

            binder.inventory.clearContent();
            binder.clearTitle();
            ItemStack folder = new ItemStack(WritingDeskRegistry.FOLDER.get());
            List<CompoundTag> folderPages = new ArrayList<>();
            CompoundTag panel = new CompoundTag();
            PageData.makeLinkPanel(panel);
            folderPages.add(panel);
            CompoundTag last = new CompoundTag();
            last.putString("OrderMarker", "last");
            folderPages.add(last);
            PageCollectionItem.setPages(folder, folderPages);
            require(!BookBinderBlockEntity.isCover(folder), "Nonempty folder accepted as a cover");
            require(binder.transferFolder(folder, 0) && binder.pageCount() == 2 && PageCollectionItem.pages(folder).isEmpty(), "Folder import failed");
            require(BookBinderBlockEntity.isCover(folder), "Empty folder not accepted as classic cover");
            require(binder.transferFolder(folder, 0) && binder.pageCount() == 0 && PageCollectionItem.pages(folder).equals(folderPages), "Folder export lost page payload/order");
            first.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            require(!one.stillValid(first) && !one.clickMenuButton(first, BookBinderMenu.TITLE_CHAR | 'X'), "Distant player can mutate binder");
            passed.add("binder: ordered folder import/export preserves payloads, only empty folders qualify as covers, and distant menus cannot mutate state");
        } finally {
            if (level.getBlockEntity(pos) instanceof BookBinderBlockEntity binder) binder.inventory.clearContent();
            level.setBlock(pos, previous, 3);
            if (previousTag != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(previousTag);
        }
        return passed;
    }
    private static boolean dispatch(ServerPlayer player, int menuId, int command) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MenuActions.Action original = new MenuActions.Action(menuId, command);
            original.encode(buffer);
            MenuActions.Action decoded = MenuActions.Action.decode(buffer);
            require(original.equals(decoded) && buffer.readableBytes() == 0, "Menu command truncated or left unread data during VarInt roundtrip");
            return MenuActions.handle(player, decoded);
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
