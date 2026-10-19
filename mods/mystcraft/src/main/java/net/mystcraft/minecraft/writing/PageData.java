package net.mystcraft.minecraft.writing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.MystcraftItems;

/** Classic page payloads. Reads never mutate stacks; ordered collections copy all unknown data. */
public final class PageData {
    public static final String PAGES = "Pages";
    private PageData() {}

    public static CompoundTag payload(ItemStack page) {
        CompoundTag result = page.hasTag() ? page.getTag().copy() : new CompoundTag();
        if (isSeparateLinkPanel(page)) makeLinkPanel(result);
        return result;
    }
    public static void applyPayload(ItemStack page, CompoundTag payload) {
        if (!page.isEmpty()) page.setTag(payload.copy());
    }
    public static Optional<String> legacySymbol(ItemStack page) { return legacySymbol(payload(page)); }
    public static Optional<String> legacySymbol(CompoundTag page) {
        if (!page.contains("symbol", Tag.TAG_STRING) || page.getString("symbol").isBlank()) return Optional.empty();
        return Optional.of(page.getString("symbol"));
    }
    public static Optional<ResourceLocation> symbol(ItemStack page) { return symbol(payload(page)); }
    public static Optional<ResourceLocation> symbol(CompoundTag page) {
        return legacySymbol(page).flatMap(PageData::identifier);
    }
    /** A normalized view only; never rewrites the original legacy token. */
    public static Optional<ResourceLocation> identifier(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(ResourceLocation.tryParse(token.toLowerCase(Locale.ROOT)));
    }
    public static void setSymbol(ItemStack page, ResourceLocation symbol) { setLegacySymbol(page, symbol.toString()); }
    public static void setLegacySymbol(ItemStack page, String symbol) {
        if (page.isEmpty()) return;
        setLegacySymbol(page.getOrCreateTag(), symbol);
    }
    public static void setLegacySymbol(CompoundTag page, String symbol) {
        if (symbol == null || symbol.isBlank()) page.remove("symbol");
        else page.putString("symbol", symbol);
    }
    private static boolean isSeparateLinkPanel(ItemStack page) {
        return MystcraftItems.LINKING_PANEL != null && page.is(MystcraftItems.LINKING_PANEL.get());
    }
    public static boolean isLinkPanel(ItemStack page) {
        return isSeparateLinkPanel(page) || page.hasTag() && isLinkPanel(page.getTag());
    }
    public static boolean isLinkPanel(CompoundTag page) { return page.contains("linkpanel", Tag.TAG_COMPOUND); }
    public static boolean isBlank(ItemStack page) { return !isLinkPanel(page) && legacySymbol(page).isEmpty(); }
    public static void makeLinkPanel(ItemStack page) { if (!page.isEmpty()) makeLinkPanel(page.getOrCreateTag()); }
    public static void makeLinkPanel(CompoundTag page) {
        if (!isLinkPanel(page)) page.put("linkpanel", new CompoundTag());
    }
    public static List<String> linkProperties(ItemStack page) { return linkProperties(payload(page)); }
    public static List<String> linkProperties(CompoundTag page) {
        ListTag list = page.getCompound("linkpanel").getList("properties", Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
        return List.copyOf(result);
    }
    public static void addLinkProperty(ItemStack page, String property) {
        if (page.isEmpty()) return;
        CompoundTag tag = page.getOrCreateTag();
        makeLinkPanel(tag);
        CompoundTag panel = tag.getCompound("linkpanel");
        ListTag list = panel.getList("properties", Tag.TAG_STRING);
        list.add(StringTag.valueOf(property));
        panel.put("properties", list);
    }
    public static Optional<Integer> quality(ItemStack page, String trait) {
        CompoundTag qualities = payload(page).getCompound("Quality");
        return qualities.contains(trait, Tag.TAG_ANY_NUMERIC) ? Optional.of(qualities.getInt(trait)) : Optional.empty();
    }
    public static void setQuality(ItemStack page, String trait, int quality) {
        if (page.isEmpty()) return;
        CompoundTag data = page.getOrCreateTag();
        CompoundTag qualities = data.getCompound("Quality");
        qualities.putInt(trait, quality);
        data.put("Quality", qualities);
    }
    /** Entries are page payloads, not ItemStack serialization wrappers. Duplicates are significant. */
    public static List<CompoundTag> readPages(CompoundTag book) {
        ListTag list = book.getList(PAGES, Tag.TAG_COMPOUND);
        List<CompoundTag> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(list.getCompound(i).copy());
        return List.copyOf(result);
    }
    public static void writePages(CompoundTag book, List<CompoundTag> pages) {
        ListTag list = new ListTag();
        for (CompoundTag page : pages) list.add(page.copy());
        book.put(PAGES, list);
    }
}
