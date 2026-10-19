package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageCollectionMenu;
import net.mystcraft.minecraft.writing.PageData;

/** Portable menu transactions against real player inventories; no client symbol payloads. */
public final class PageCollectionSmoke {
    private PageCollectionSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        ServerPlayer player = new ServerPlayer(server, level,
                new GameProfile(UUID.fromString("2c51330c-ae13-4c33-952f-5d17332cad21"), "CollectionSmoke"), null);
        player.getInventory().clearContent();
        ItemStack folder = new ItemStack(WritingDeskRegistry.FOLDER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, folder);
        AbstractContainerMenu menu = new PageCollectionMenu(31, player.getInventory(), InteractionHand.MAIN_HAND);
        require(menu.stillValid(player), "Portable mainhand collection invalid initially");
        require(!menu.getSlot(0).mayPickup(player) && !menu.getSlot(28).mayPickup(player), "Opened collection can be picked out of its own menu");
        ItemStack pages = page(); pages.setCount(3); ItemStack pageSnapshot = pages.copy();
        player.getInventory().setItem(9, pages);
        menu.clicked(1, 0, ClickType.SWAP, player);
        require(((Player) player).getItemInHand(InteractionHand.MAIN_HAND) == folder && pages.getCount() == 3, "Number-key swap moved held collection");
        require(!menu.quickMoveStack(player, 1).isEmpty(), "Shift-click page bulk storage failed");
        require(player.getInventory().getItem(9).isEmpty() && PageCollectionItem.pages(folder).size() == 3, "Bulk storage duplicated or lost physical pages");
        List<CompoundTag> saved = PageCollectionItem.pages(folder);
        for (CompoundTag payload : saved) require(payload.equals(PageData.payload(pageSnapshot)), "Bulk storage changed page NBT or duplicate count");
        require(menu.clickMenuButton(player, PageCollectionMenu.EXTRACT), "Portable extraction failed");
        require(menu.getCarried().getCount() == 1 && menu.getCarried().getTag().equals(pageSnapshot.getTag()) && PageCollectionItem.pages(folder).size() == 2, "Portable extraction did not move exactly one unchanged page");
        require(menu.clickMenuButton(player, PageCollectionMenu.INSERT) && menu.getCarried().isEmpty()
                && PageCollectionItem.pages(folder).equals(saved), "Portable insertion did not restore exact ordered duplicates");
        passed.add("portable collections: mainhand menu stores real shift-clicked pages, locks held slot and number swaps, and preserves exact ordered duplicate NBT on insert/extract");

        CompoundTag first = PageData.payload(pageSnapshot), second = first.copy(), third = first.copy();
        first.putString("SmokeOrder", "A"); second.putString("SmokeOrder", "B"); third.putString("SmokeOrder", "C");
        PageCollectionItem.setPages(folder, List.of(first, second, third));
        require(menu.clickMenuButton(player, PageCollectionMenu.REORDER_BASE + 2), "Folder drag reorder failed");
        require(PageCollectionItem.pages(folder).equals(List.of(second, third, first)), "Drag reorder lost nested payloads or insertion order");
        require(!menu.clickMenuButton(player, PageCollectionMenu.REORDER_BASE + 255), "Out-of-range drag target accepted");
        passed.add("portable collections: direct drag order moves one exact payload and rejects out-of-range indices");

        PageCollectionItem.setPages(folder, List.of(PageData.payload(pageSnapshot)));
        require(menu.clickMenuButton(player, 0), "Reset selection after replacing fixture pages");
        AbstractContainerMenu old = new PageCollectionMenu(32, player.getInventory(), InteractionHand.MAIN_HAND);
        require(menu.clickMenuButton(player, PageCollectionMenu.EXTRACT), "First view did not extract only page");
        require(!old.clickMenuButton(player, PageCollectionMenu.EXTRACT) && old.getCarried().isEmpty(), "Second view duplicated extracted page");
        menu.setCarried(ItemStack.EMPTY);
        List<CompoundTag> full = new ArrayList<>();
        for (int i = 0; i < PageCollectionItem.MAX_PAGES; i++) full.add(PageData.payload(pageSnapshot));
        PageCollectionItem.setPages(folder, full);
        ItemStack extra = page(); extra.setCount(2); player.getInventory().setItem(9, extra);
        require(menu.quickMoveStack(player, 1).isEmpty() && extra.getCount() == 2 && PageCollectionItem.pages(folder).size() == 256, "Full portable collection consumed input or exceeded capacity");
        ((Player) player).setItemInHand(InteractionHand.MAIN_HAND, folder.copy());
        require(!menu.stillValid(player) && !menu.clickMenuButton(player, PageCollectionMenu.EXTRACT), "Identical held collection substitution accepted by old menu");
        passed.add("portable collections: two views cannot duplicate extraction, 256-page capacity preserves overflow and identical-stack replacement invalidates stale actions");

        ItemStack portfolio = new ItemStack(WritingDeskRegistry.PORTFOLIO.get());
        PageCollectionItem.setPages(portfolio, List.of(PageData.payload(pageSnapshot), PageData.payload(pageSnapshot)));
        ((Player) player).setItemInHand(InteractionHand.OFF_HAND, portfolio);
        AbstractContainerMenu offhand = new PageCollectionMenu(33, player.getInventory(), InteractionHand.OFF_HAND);
        require(offhand.stillValid(player), "Portable offhand collection invalid initially");
        offhand.clicked(1, 40, ClickType.SWAP, player);
        require(((Player) player).getItemInHand(InteractionHand.OFF_HAND) == portfolio, "Offhand swap moved open portfolio");
        require(!offhand.clickMenuButton(player, PageCollectionMenu.REORDER_BASE + 1), "Unordered portfolio accepted drag reorder");
        require(!offhand.clickMenuButton(player, PageCollectionMenu.DOWN), "Unordered portfolio accepted ordered folder controls");
        require(offhand.clickMenuButton(player, PageCollectionMenu.EXTRACT) && PageCollectionItem.pages(portfolio).size() == 1, "Offhand extraction failed");
        passed.add("portable collections: offhand inventory is synchronized and locked; portfolios extract physical pages while rejecting folder order operations");
        offhand.setCarried(ItemStack.EMPTY);
        CompoundTag unique = PageData.payload(pageSnapshot); unique.putString("BulkMarker", "exact");
        CompoundTag different = unique.copy(); different.putInt("Other", 1);
        PageCollectionItem.setPages(portfolio, List.of(unique, unique.copy(), different));
        require(offhand.clickMenuButton(player, 0), "Bulk fixture must explicitly select first page");
        require(offhand.clickMenuButton(player, PageCollectionMenu.EXTRACT_ALL) && offhand.getCarried().getCount() == 2
                && PageCollectionItem.pages(portfolio).equals(List.of(different)), "Grouped portable take mixed distinct metadata or lost owned counts");
        require(offhand.clickMenuButton(player, PageCollectionMenu.INSERT_ALL) && offhand.getCarried().isEmpty()
                && PageCollectionItem.pages(portfolio).equals(List.of(unique, unique, different)), "Portable bulk insertion lost cursor pages or changed exact payloads");
        passed.add("portable collections: grouped exact-NBT bulk take and all-cursor insertion preserve distinct metadata and physical page counts");
        return passed;
    }
    private static ItemStack page() {
        ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
        PageData.setLegacySymbol(page, "NormalLighting");
        CompoundTag nested = new CompoundTag(); nested.putString("Preserved", "portable"); nested.putInt("Quality", 73);
        page.getOrCreateTag().put("SmokeNested", nested); return page;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
