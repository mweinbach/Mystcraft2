package net.mystcraft.minecraft.writing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluid;
import net.mystcraft.minecraft.instability.InstabilityProfile;

/** Restart-only, bounded operator equivalents of classic fluid/rank configuration and block-factor IMC. */
public final class SymbolTuning {
    public record FluidRule(boolean blacklisted, boolean seaBanned, Integer grammarRank, Integer cardRank, float accessible, float total) {}
    public record BiomeRule(boolean blacklisted, Integer grammarRank, Integer cardRank) {}
    private static final Map<String, FluidRule> LEGACY = legacyDefaults();
    private static volatile Map<String, JsonObject> fluids = Map.of(), biomes = Map.of();
    private static volatile Map<String, Integer> cardRanks = Map.of();
    private SymbolTuning() {}
    /** Call after loader native fluid attributes are wired, before any baseline/profile or new Age compile. */
    public static void initialize(MinecraftServer server) {
        var path = server.getServerDirectory().toPath().resolve("config/mystcraft-symbols.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "{\n  \"fluids\": {},\n  \"biomes\": {},\n  \"cardRanks\": {},\n  \"watchedBlocks\": {}\n}\n", StandardCharsets.UTF_8);
            }
            if (Files.size(path) > 1_048_576) throw new IllegalArgumentException("Symbol tuning file exceeds1MiB");
            JsonObject root;
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { root = JsonParser.parseReader(reader).getAsJsonObject(); }
            configure(root, server);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Invalid config/mystcraft-symbols.json; refusing partial symbol/factor configuration", error);
        }
    }
    public static void clear(MinecraftServer server) {
        fluids = Map.of(); biomes = Map.of(); cardRanks = Map.of(); InstabilityProfile.resetFactors();
    }
    public static FluidRule legacyDefault(String key) { return LEGACY.getOrDefault(key, new FluidRule(false, false, 4, 4, 1, .25F)); }
    public static FluidRule overrideFluid(FluidRule defaults, JsonObject value) {
        validateFluid(value);
        return new FluidRule(bool(value, "blacklisted", false), bool(value, "seaBanned", defaults.seaBanned()),
                value.has("grammarRank") ? rank(value.get("grammarRank")) : defaults.grammarRank(),
                value.has("cardRank") ? rank(value.get("cardRank")) : defaults.cardRank(),
                number(value, "accessible", defaults.accessible()), number(value, "total", defaults.total()));
    }
    public static void configure(JsonObject root, MinecraftServer server) {
        keys(root, Set.of("fluids", "biomes", "cardRanks", "watchedBlocks"));
        var nextFluids = objects(root, "fluids"); var nextBiomes = objects(root, "biomes");
        var nextCards = new LinkedHashMap<String, Integer>();
        for (var entry : object(root, "cardRanks").entrySet()) {
            id(entry.getKey()); nextCards.put(entry.getKey(), rank(entry.getValue()));
        }
        var nextWatched = objects(root, "watchedBlocks");
        for (var entry : nextFluids.entrySet()) {
            ResourceLocation id = id(entry.getKey());
            if (!Registry.FLUID.containsKey(id)) throw new IllegalArgumentException("Unknown fluid " + id);
            validateFluid(entry.getValue());
        }
        for (var entry : nextBiomes.entrySet()) {
            if (!server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).containsKey(id(entry.getKey()))) throw new IllegalArgumentException("Unknown biome " + entry.getKey());
            JsonObject value = entry.getValue(); keys(value, Set.of("blacklisted", "grammarRank", "cardRank"));
            bool(value, "blacklisted", false); if (value.has("grammarRank")) rank(value.get("grammarRank")); if (value.has("cardRank")) rank(value.get("cardRank"));
        }
        var factors = new LinkedHashMap<String, InstabilityProfile.Factors>();
        for (var entry : nextWatched.entrySet()) {
            if (!Registry.BLOCK.containsKey(id(entry.getKey()))) throw new IllegalArgumentException("Unknown watched block " + entry.getKey());
            keys(entry.getValue(), Set.of("accessible", "total"));
            factors.put(entry.getKey(), new InstabilityProfile.Factors(number(entry.getValue(), "accessible", 0), number(entry.getValue(), "total", 0)));
        }
        // Validate the entire candidate before publishing any change. Runtime reload is deliberately unsupported.
        fluids = Map.copyOf(nextFluids); biomes = Map.copyOf(nextBiomes);
        cardRanks = java.util.Collections.unmodifiableMap(nextCards);
        InstabilityProfile.resetFactors();
        for (var choice : DynamicSymbolPages.fluidChoices()) {
            var fluid = Registry.FLUID.get(new ResourceLocation(choice.payload().getString("Fluid")));
            var rule = fluid(fluid, DynamicSymbolPages.fluidDisplayName(fluid));
            var block = fluid.defaultFluidState().createLegacyBlock().getBlock();
            InstabilityProfile.FACTORS.put(Registry.BLOCK.getKey(block).toString(), new InstabilityProfile.Factors(rule.accessible(), rule.total()));
        }
        InstabilityProfile.FACTORS.putAll(factors);
    }
    public static FluidRule fluid(Fluid fluid, Component displayName) {
        String id = Registry.FLUID.getKey(fluid).toString();
        String legacyKey = displayName.getContents() instanceof TranslatableContents translated ? translated.getKey().toLowerCase(java.util.Locale.ROOT).replace(' ', '_') : "";
        // This is Mystcraft's own exact legacy identity, not a guessed alias for another mod's oil/ore.
        if (id.equals("mystcraft:black_ink")) legacyKey = "fluid.myst.ink.black";
        var defaults = legacyDefault(legacyKey);
        JsonObject value = fluids.get(id);
        if (value == null) return defaults;
        return overrideFluid(defaults, value);
    }
    public static BiomeRule biome(ResourceLocation id) {
        boolean end = id.toString().equals("minecraft:the_end");
        JsonObject value = biomes.get(id.toString());
        if (value == null) return new BiomeRule(false, end ? null : 1, end ? null : 2);
        return new BiomeRule(bool(value, "blacklisted", false), value.has("grammarRank") ? rank(value.get("grammarRank")) : end ? null : 1,
                value.has("cardRank") ? rank(value.get("cardRank")) : end ? null : 2);
    }
    public static Integer cardRank(String symbol, Integer fallback) { return cardRanks.containsKey(symbol) ? cardRanks.get(symbol) : fallback; }
    private static void validateFluid(JsonObject value) {
        keys(value, Set.of("blacklisted", "seaBanned", "grammarRank", "cardRank", "accessible", "total"));
        bool(value, "blacklisted", false); bool(value, "seaBanned", false);
        if (value.has("grammarRank")) rank(value.get("grammarRank")); if (value.has("cardRank")) rank(value.get("cardRank"));
        number(value, "accessible", 1); number(value, "total", .25F);
    }
    private static Map<String, JsonObject> objects(JsonObject root, String key) {
        var result = new LinkedHashMap<String, JsonObject>();
        for (var entry : object(root, key).entrySet()) {
            id(entry.getKey()); if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException(key + " values must be objects");
            result.put(entry.getKey(), entry.getValue().getAsJsonObject().deepCopy());
        }
        return result;
    }
    private static JsonObject object(JsonObject root, String key) {
        if (!root.has(key)) return new JsonObject();
        if (!root.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        JsonObject value = root.getAsJsonObject(key); if (value.size() > 4096) throw new IllegalArgumentException("Too many " + key + " entries"); return value;
    }
    private static ResourceLocation id(String text) { ResourceLocation id = ResourceLocation.tryParse(text); if (id == null || text.length() > 256) throw new IllegalArgumentException("Invalid registry ID"); return id; }
    private static Integer rank(JsonElement value) {
        if (value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Rank must be null or integer0..16");
        int result; try { result = value.getAsBigDecimal().intValueExact(); } catch (ArithmeticException bad) { throw new IllegalArgumentException("Rank must be an integer", bad); }
        if (result < 0 || result > 16) throw new IllegalArgumentException("Rank must be0..16"); return result;
    }
    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        var value = object.get(key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be boolean"); return value.getAsBoolean();
    }
    private static float number(JsonObject object, String key, float fallback) {
        if (!object.has(key)) return fallback;
        var value = object.get(key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be numeric");
        float result = value.getAsFloat(); if (!Float.isFinite(result) || Math.abs(result) > 1_000_000) throw new IllegalArgumentException("Factor must be finite and bounded"); return result;
    }
    private static void keys(JsonObject object, Set<String> known) { for (String key : object.keySet()) if (!known.contains(key)) throw new IllegalArgumentException("Unknown symbol setting " + key); }
    private static Map<String, FluidRule> legacyDefaults() {
        try (var input = SymbolTuning.class.getResourceAsStream("/assets/mystcraft/classic_fluid_defaults.json")) {
            if (input == null) throw new IllegalStateException("Missing source fluid defaults");
            var result = new LinkedHashMap<String, FluidRule>();
            for (var entry : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("fluids").entrySet()) {
                var value = entry.getValue().getAsJsonObject(); result.put(entry.getKey(), new FluidRule(false, value.get("seaBanned").getAsBoolean(), 4, 4, value.get("accessible").getAsFloat(), value.get("total").getAsFloat()));
            }
            return Map.copyOf(result);
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
}
