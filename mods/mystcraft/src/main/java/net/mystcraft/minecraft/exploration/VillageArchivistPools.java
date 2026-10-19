package net.mystcraft.minecraft.exploration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Additive native village-house pool adapter. Never replaces another mod's weighted entries. */
public final class VillageArchivistPools {
    public record Settings(boolean villageDeskGen, boolean archivistEnabled) {}
    private static volatile Settings settings = new Settings(true, true);
    private VillageArchivistPools() {}
    public static Settings settings() { return settings; }
    public static void initialize(MinecraftServer server) {
        Path file = server.getServerDirectory().toPath().resolve("config/mystcraft-village.json");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "{\n  \"villageDeskGen\": true,\n  \"archivistEnabled\": true\n}\n");
            }
            if (Files.size(file) > 65536) throw new IllegalArgumentException("Village configuration too large");
            var root = com.google.gson.JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject()) throw new IllegalArgumentException("Village configuration must be object");
            var json = root.getAsJsonObject();
            settings = new Settings(read(json, "villageDeskGen"), read(json, "archivistEnabled"));
        } catch (java.io.IOException | IllegalArgumentException | com.google.gson.JsonParseException failure) {
            throw new IllegalStateException("Invalid Mystcraft village settings: " + file, failure);
        }
    }
    private static boolean read(com.google.gson.JsonObject json, String key) {
        if (!json.has(key)) return true;
        var value = json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be boolean");
        return value.getAsBoolean();
    }
    public static boolean houses(StructureTemplatePool pool) {
        var id = pool.getName();
        return id.getNamespace().equals("minecraft") && java.util.Set.of("village/plains/houses", "village/desert/houses",
                "village/savanna/houses", "village/snowy/houses", "village/taiga/houses").contains(id.getPath());
    }
    public static List<StructurePoolElement> expand(StructureTemplatePool pool, List<StructurePoolElement> original, RandomSource random) {
        if (!houses(pool)) return original;
        var expanded = new ArrayList<>(original);
        var house = ArchivistHouseElement.create();
        for (int i = 0; i < 20; i++) expanded.add(house);
        for (int i = expanded.size(); i > 1; i--) java.util.Collections.swap(expanded, i - 1, random.nextInt(i));
        return expanded;
    }
}
