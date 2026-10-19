package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.mystcraft.minecraft.exploration.ExplorationPages;
import net.mystcraft.minecraft.item.BoosterPackItem;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;

/** Registry-aware production pool checks; never fabricates a modded fluid on a vanilla-only server. */
public final class DynamicPagesSmoke {
    private DynamicPagesSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        var registry = level.registryAccess(); var choices = DynamicSymbolPages.choices(registry);
        int biomes = 0, fluids = 0, modded = 0;
        for (var choice : choices) {
            String symbol = choice.payload().getString("symbol");
            if (symbol.startsWith("mystcraft:biome/")) {
                biomes++; if (!symbol.startsWith("mystcraft:biome/minecraft/")) modded++;
                boolean end = symbol.equals("mystcraft:biome/minecraft/the_end");
                if (end ? choice.rank() != null : choice.rank() == null || choice.rank() != 2)
                    throw new AssertionError("Source biome card rank/End exception lost");
            } else if (symbol.startsWith("mystcraft:fluid/")) {
                fluids++; if (choice.rank() == null || choice.rank() != 4 || !choice.payload().contains("Fluid")
                        || !choice.payload().contains("BlockState")) throw new AssertionError("Native fluid source identity/rank missing");
            }
            if (choice.poem().size() != 4 || !DynamicSymbolPages.isDynamic(choice.payload())) throw new AssertionError("Dynamic glyph metadata missing");
            CompoundTag leaked = choice.payload(); leaked.putString("Mutated", "outside");
            if (choice.payload().contains("Mutated")) throw new AssertionError("Dynamic pool exposes mutable canonical payload");
        }
        if (biomes != registry.registryOrThrow(Registry.BIOME_REGISTRY).size()) throw new AssertionError("Not every live biome has a native page choice");
        if (!DynamicSymbolPages.choices(null).isEmpty()) throw new AssertionError("Dynamic registry leaked into a disconnected/unbound lookup");
        boolean acquiredBiome = false; RandomSource random = RandomSource.create(92841);
        for (int i = 0; i < 128; i++) {
            var page = ExplorationPages.sampleRank(registry, random, 2, 2);
            if (PageData.legacySymbol(page).orElse("").startsWith("mystcraft:biome/")) acquiredBiome = true;
        }
        if (!acquiredBiome) throw new AssertionError("Real rank2 production collector pool cannot acquire biome pages");
        var booster = BoosterPackItem.generate(registry, RandomSource.create(673));
        var pages = PageCollectionItem.pages(booster); int[] actual = new int[4];
        for (var payload : pages) {
            var page = new net.minecraft.world.item.ItemStack(net.mystcraft.integration.registry.AgeRegistry.PAGE.get());
            PageData.applyPayload(page, payload); int rank = ExplorationPages.rank(page, registry);
            if (rank < 0) throw new AssertionError("Production booster emitted an unranked/unrecognized dynamic page");
            actual[Math.min(3, rank)]++;
        }
        if (!java.util.Arrays.equals(actual, new int[]{7,4,4,1})) throw new AssertionError("Production booster source7/4/4/1 rank partition lost");
        return List.of("dynamic pages: native registry pool includes " + biomes + " biome choices (" + modded + " modded) and " + fluids + " validated source-fluid choices, with source ranks and four-word poems",
                "dynamic pages: registry lookup isolation and copied payloads prevent cross-world/client leakage; real rank2 collector acquires biome pages",
                "dynamic pages: production registry-aware booster retains exact7/4/4/1 rank partitions including dynamic parameters and unbounded rare ranks");
    }
}
