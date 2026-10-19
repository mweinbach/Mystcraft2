package net.mystcraft.minecraft.exploration;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Source card ranks and normalization, evaluated against the current world's executable page pool. */
public final class ExplorationPages {
    private static final Map<String, Integer> RANKS = ranks();
    private ExplorationPages() {}
    /** Static-only compatibility view; production acquisition uses the registry-aware overload. */
    public static List<SymbolRegistry.Definition> available() {
        return SymbolRegistry.all().stream().filter(symbol -> symbol.implemented()
                || net.mystcraft.minecraft.age.generation.ClassicMaterials.find(symbol.id().toString()) != null)
                .filter(symbol -> net.mystcraft.minecraft.writing.SymbolTuning.cardRank(symbol.id().toString(), RANKS.get(symbol.id().toString())) != null).toList();
    }
    public static List<DynamicSymbolPages.Choice> available(RegistryAccess registry) {
        var result = new ArrayList<DynamicSymbolPages.Choice>();
        for (var symbol : available()) {
            var payload = new net.minecraft.nbt.CompoundTag(); PageData.setLegacySymbol(payload, symbol.legacyId());
            result.add(new DynamicSymbolPages.Choice(payload, rank(symbol), Component.literal(symbol.displayName()), symbol.poem()));
        }
        for (var choice : DynamicSymbolPages.choices(registry)) if (choice.rank() != null) result.add(choice);
        return List.copyOf(result);
    }
    public static ItemStack page(DynamicSymbolPages.Choice choice) {
        ItemStack page = new ItemStack(AgeRegistry.PAGE.get()); PageData.applyPayload(page, choice.payload()); return page;
    }
    public static ItemStack sample(RandomSource random, boolean single) { return sample(null, random, single); }
    public static ItemStack sample(RegistryAccess registry, RandomSource random, boolean single) {
        var all = available(registry); var selected = select(all, all, random);
        if (selected == null) return ItemStack.EMPTY;
        int rank = selected.rank(); int maximum = rank < 4 ? 16 >> rank : 1;
        ItemStack page = page(selected);
        page.setCount(single ? 1 : 1 + random.nextInt(Math.max(1, maximum - 1)));
        return page;
    }
    public static ItemStack sampleRank(RandomSource random, int minimum, int maximum) { return sampleRank(null, random, minimum, maximum); }
    public static ItemStack sampleRank(RegistryAccess registry, RandomSource random, int minimum, int maximum) {
        var all = available(registry);
        var eligible = all.stream().filter(symbol -> symbol.rank() >= minimum && symbol.rank() <= maximum).toList();
        var selected = select(all, eligible, random); return selected == null ? ItemStack.EMPTY : page(selected);
    }
    /** Exact rank normalization across the full pool, then source nextFloat weighted selection. */
    private static DynamicSymbolPages.Choice select(List<DynamicSymbolPages.Choice> all, List<DynamicSymbolPages.Choice> eligible, RandomSource random) {
        int[] weights = treasureWeights(all);
        float total = 0; for (var symbol : eligible) total += weights[symbol.rank()];
        if (total <= 0) return null;
        float choice = random.nextFloat() * total;
        DynamicSymbolPages.Choice last = null;
        for (var symbol : eligible) {
            choice -= weights[symbol.rank()]; last = symbol;
            if (choice <= 0) return symbol;
        }
        return last;
    }
    static int[] treasureWeights(List<DynamicSymbolPages.Choice> all) {
        int highest = all.stream().mapToInt(DynamicSymbolPages.Choice::rank).max().orElse(0);
        int[] sizes = new int[highest + 1], weights = new int[highest + 1];
        for (var symbol : all) sizes[symbol.rank()]++;
        int weight = 1, previous = 0;
        for (int rank = highest; rank >= 0; rank--) {
            if (weight != 1 && sizes[rank] > 0) weight = Math.max(weight, previous / sizes[rank] + 1);
            weights[rank] = weight; previous = sizes[rank] * weight; weight++;
        }
        return weights;
    }

    public static ItemStack sampleLectern(RandomSource random) { return sampleLectern(null, random); }
    public static ItemStack sampleLectern(RegistryAccess registry, RandomSource random) {
        for (int attempt = 0; attempt < 100; attempt++) {
            for (ItemStack page : ClassicTreasure.generate(registry, random, 0)) {
                page.setCount(1);
                if (net.mystcraft.minecraft.exploration.MystLecternBlockEntity.accepts(page)
                        && (!page.is(net.mystcraft.integration.registry.AgeRegistry.PAGE.get()) || rank(page, registry) >= 3)) return page;
            }
        }
        return ItemStack.EMPTY;
    }
    public static int rank(ItemStack page, RegistryAccess registry) {
        var symbol = PageData.symbol(page);
        if (symbol.isEmpty()) return -1;
        Integer known = net.mystcraft.minecraft.writing.SymbolTuning.cardRank(symbol.get().toString(), RANKS.get(symbol.get().toString())); if (known != null) return known;
        for (var choice : DynamicSymbolPages.choices(registry))
            if (PageData.symbol(choice.payload()).equals(symbol)) return choice.rank() == null ? -1 : choice.rank();
        return -1;
    }
    public static int rank(SymbolRegistry.Definition symbol) {
        Integer rank = net.mystcraft.minecraft.writing.SymbolTuning.cardRank(symbol.id().toString(), RANKS.getOrDefault(symbol.id().toString(), 2)); return rank == null ? -1 : rank;
    }
    private static Map<String, Integer> ranks() {
        Map<String, Integer> result = new HashMap<>();
        try (var input = ExplorationPages.class.getResourceAsStream("/assets/mystcraft/exploration/card_ranks.json")) {
            if (input == null) throw new IllegalStateException("Missing exploration card-rank data");
            var object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("ranks");
            object.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsInt()));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        return Map.copyOf(result);
    }
}
