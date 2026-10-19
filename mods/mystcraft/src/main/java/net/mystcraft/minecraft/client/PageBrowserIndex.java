package net.mystcraft.minecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.minecraft.writing.PageData;

/** Display indices only: filtering/sorting never rewrites physical portfolio page order. */
final class PageBrowserIndex {
    private PageBrowserIndex() {}
    record Entry(int index, ItemStack page, int count) {}
    static List<Entry> entries(List<CompoundTag> pages, String query, boolean alphabetical, boolean grouped, boolean showAll) {
        var result = new ArrayList<Entry>();
        var unique = new java.util.LinkedHashMap<CompoundTag, Integer>();
        for (int i = 0; i < pages.size(); i++) {
            CompoundTag payload = pages.get(i);
            Integer previous = grouped ? unique.get(payload) : null;
            if (previous != null) {
                Entry entry = result.get(previous); result.set(previous, new Entry(entry.index(), entry.page(), entry.count() + 1));
            } else { unique.put(payload, result.size()); result.add(new Entry(i, page(payload), 1)); }
        }
        if (grouped && showAll) for (var symbol : net.mystcraft.minecraft.writing.SymbolRegistry.all()) {
            CompoundTag payload = new CompoundTag(); PageData.setLegacySymbol(payload, symbol.legacyId());
            boolean owned = result.stream().anyMatch(entry -> PageData.symbol(entry.page()).filter(symbol.id()::equals).isPresent());
            if (!owned) result.add(new Entry(-1, page(payload), 0));
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        if (grouped && showAll && client.level != null) for (var choice : net.mystcraft.minecraft.writing.DynamicSymbolPages.choices(client.level.registryAccess())) {
            var poem = choice.poem();
            boolean owned = result.stream().anyMatch(entry -> net.mystcraft.minecraft.writing.DynamicSymbolPages.isDynamic(entry.page())
                    && net.mystcraft.minecraft.writing.DynamicSymbolPages.poem(entry.page()).equals(poem));
            if (!owned) result.add(new Entry(-1,page(choice.payload()),0));
        }
        String[] terms = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        result.removeIf(entry -> {
            String text = (entry.page().getHoverName().getString() + " " + PageData.legacySymbol(entry.page()).orElse("")).toLowerCase(Locale.ROOT);
            for (String term : terms) if (!text.contains(term)) return true;
            return false;
        });
        if (alphabetical) result.sort(Comparator.comparing(entry -> entry.page().getHoverName().getString().toLowerCase(Locale.ROOT)));
        return result;
    }

    static ItemStack page(CompoundTag payload) {
        ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
        PageData.applyPayload(page, payload); return page;
    }
    static List<Integer> matching(List<CompoundTag> pages, String query, boolean alphabetical) {
        String[] terms = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        List<Integer> result = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String name = page(pages.get(i)).getHoverName().getString().toLowerCase(Locale.ROOT);
            names.add(name);
            String searchable = name + " " + pages.get(i).getString("symbol").toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String term : terms) if (!searchable.contains(term)) { matches = false; break; }
            if (matches) result.add(i);
        }
        if (alphabetical) result.sort(Comparator.comparing((Integer index) -> names.get(index)).thenComparingInt(Integer::intValue));
        return result;
    }
}
