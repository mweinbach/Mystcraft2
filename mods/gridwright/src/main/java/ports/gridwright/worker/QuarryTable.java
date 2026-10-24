package ports.gridwright.worker;

import ports.gridwright.registry.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * The virtual world the quantum quarry mines. Each draw picks a depth at random, then a block: one of
 * the entries that occur at that depth, by weight out of {@link #TOTAL}, or the region's base stone.
 * The weights are Gridwright's own, chosen to look like a strip mine (about 2.5% vanilla ore in the
 * overworld), not copied from any mod.
 *
 * <p>The biome on the quarry's marker picks the region (overworld, nether or end) and adjusts it:
 * badlands have four times the gold, mountains have emeralds, oceans clay and extra gravel, deserts
 * sand and sandstone near the top. Modded ores join through the {@code c:ores} and {@code forge:ores}
 * block tags, each at {@link #MODDED_WEIGHT}: ids naming the nether or the end go to those regions,
 * {@code deepslate_} variants to the deep layers. {@code gridwright:quantum_quarry_excluded} removes
 * any block from every table.
 */
public final class QuarryTable {

    /** Weights are parts of this many blocks at a depth. */
    public static final int TOTAL = 10_000;
    /** Weight of every modded ore found through the ore tags, in its depth band. */
    public static final int MODDED_WEIGHT = 20;

    public enum Region {
        OVERWORLD(-64, 63), NETHER(0, 127), END(0, 127);

        final int minY;
        final int maxY;

        Region(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public enum Terrain { NORMAL, BADLANDS, MOUNTAIN, OCEAN, DESERT }

    public record Entry(BlockState state, int weight, int minY, int maxY) {
        boolean covers(int y) {
            return y >= minY && y <= maxY;
        }
    }

    private final Region region;
    private final List<Entry> entries;

    private QuarryTable(Region region, List<Entry> entries) {
        this.region = region;
        this.entries = List.copyOf(entries);
    }

    public Region region() {
        return region;
    }

    public List<Entry> entries() {
        return entries;
    }

    public static Region region(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_NETHER)) return Region.NETHER;
        if (biome.is(BiomeTags.IS_END)) return Region.END;
        return Region.OVERWORLD;
    }

    public static Terrain terrain(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_BADLANDS)) return Terrain.BADLANDS;
        if (biome.is(BiomeTags.IS_MOUNTAIN)) return Terrain.MOUNTAIN;
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return Terrain.OCEAN;
        if (biome.is(BiomeTags.HAS_DESERT_PYRAMID)) return Terrain.DESERT;
        return Terrain.NORMAL;
    }

    /** The table for {@code biome}, with the modded ores currently in the ore tags. */
    public static QuarryTable forBiome(Holder<Biome> biome) {
        return of(region(biome), terrain(biome), taggedOres());
    }

    /** Every block in {@code c:ores} or {@code forge:ores}, in id order. */
    public static List<Block> taggedOres() {
        TreeMap<ResourceLocation, Block> ores = new TreeMap<>();
        for (TagKey<Block> tag : List.of(ModTags.C_ORES, ModTags.FORGE_ORES)) {
            Registry.BLOCK.getTag(tag).ifPresent(set -> collect(set, ores));
        }
        return List.copyOf(ores.values());
    }

    private static void collect(HolderSet.Named<Block> set, TreeMap<ResourceLocation, Block> into) {
        for (Holder<Block> holder : set) holder.unwrapKey().ifPresent(key -> into.put(key.location(), holder.value()));
    }

    /** Builds a table; {@code ores} are candidate modded ores (blocks the table already has are skipped). */
    public static QuarryTable of(Region region, Terrain terrain, Collection<Block> ores) {
        List<Entry> entries = new ArrayList<>();
        switch (region) {
            case OVERWORLD -> overworld(entries, terrain);
            case NETHER -> nether(entries);
            case END -> { }
        }
        Set<Block> known = new HashSet<>(KNOWN_ORES);
        entries.forEach(entry -> known.add(entry.state().getBlock()));
        List<Block> extras = new ArrayList<>(ores);
        extras.sort(Comparator.comparing(block -> Registry.BLOCK.getKey(block)));
        for (Block block : extras) {
            if (!known.add(block)) continue;
            String path = Registry.BLOCK.getKey(block).getPath();
            Region home = path.contains("nether") ? Region.NETHER : isEnd(path) ? Region.END : Region.OVERWORLD;
            if (home != region) continue;
            if (region == Region.OVERWORLD) {
                boolean deep = path.startsWith("deepslate_");
                entries.add(new Entry(block.defaultBlockState(), MODDED_WEIGHT, deep ? -64 : 0, deep ? -1 : 63));
            } else {
                entries.add(new Entry(block.defaultBlockState(), MODDED_WEIGHT, region.minY, region.maxY));
            }
        }
        entries.removeIf(entry -> entry.state().is(ModTags.QUANTUM_QUARRY_EXCLUDED));
        return new QuarryTable(region, entries);
    }

    private static boolean isEnd(String path) {
        return path.startsWith("end_") || path.endsWith("_end") || path.contains("_end_");
    }

    /**
     * Draws the next virtual block. If the depth's base stone is in {@code #gridwright:quantum_quarry_excluded}
     * only the remaining entries are drawn, and air (no drops) where none cover the depth.
     */
    public BlockState draw(RandomSource random) {
        int y = region.minY + random.nextInt(region.maxY - region.minY + 1);
        int total = 0;
        for (Entry entry : entries) if (entry.covers(y)) total += entry.weight();
        BlockState base = base(y);
        boolean baseAllowed = !base.is(ModTags.QUANTUM_QUARRY_EXCLUDED);
        if (!baseAllowed && total == 0) return Blocks.AIR.defaultBlockState();
        int roll = random.nextInt(baseAllowed ? Math.max(TOTAL, total) : total);
        for (Entry entry : entries) {
            if (!entry.covers(y)) continue;
            roll -= entry.weight();
            if (roll < 0) return entry.state();
        }
        return base;
    }

    /** The stone a depth is made of when no entry is drawn. */
    public BlockState base(int y) {
        return switch (region) {
            case OVERWORLD -> (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
            case NETHER -> Blocks.NETHERRACK.defaultBlockState();
            case END -> Blocks.END_STONE.defaultBlockState();
        };
    }

    /** Vanilla ores the tables place by hand (with their own weights), so the ore tags do not add them again. */
    private static final List<Block> KNOWN_ORES = List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS);

    private static void add(List<Entry> entries, Block block, int weight, int minY, int maxY) {
        entries.add(new Entry(block.defaultBlockState(), weight, minY, maxY));
    }

    private static void overworld(List<Entry> entries, Terrain terrain) {
        if (terrain == Terrain.DESERT) {
            add(entries, Blocks.SAND, 300, 32, 63);
            add(entries, Blocks.SANDSTONE, 200, 32, 63);
        } else {
            add(entries, Blocks.DIRT, 300, 0, 63);
        }
        add(entries, Blocks.GRAVEL, terrain == Terrain.OCEAN ? 400 : 200, -64, 63);
        if (terrain == Terrain.OCEAN) add(entries, Blocks.CLAY, 150, 0, 63);
        add(entries, Blocks.GRANITE, 200, 0, 63);
        add(entries, Blocks.DIORITE, 200, 0, 63);
        add(entries, Blocks.ANDESITE, 200, 0, 63);
        add(entries, Blocks.TUFF, 250, -64, -1);
        add(entries, Blocks.COAL_ORE, 100, 0, 63);
        add(entries, Blocks.DEEPSLATE_COAL_ORE, 30, -64, -1);
        add(entries, Blocks.IRON_ORE, 80, 0, 63);
        add(entries, Blocks.DEEPSLATE_IRON_ORE, 80, -64, -1);
        add(entries, Blocks.COPPER_ORE, 70, 0, 63);
        add(entries, Blocks.DEEPSLATE_COPPER_ORE, 40, -16, -1);
        int gold = terrain == Terrain.BADLANDS ? 4 : 1;
        add(entries, Blocks.GOLD_ORE, 15 * gold, 0, terrain == Terrain.BADLANDS ? 63 : 31);
        add(entries, Blocks.DEEPSLATE_GOLD_ORE, 20 * gold, -64, -1);
        add(entries, Blocks.REDSTONE_ORE, 20, 0, 15);
        add(entries, Blocks.DEEPSLATE_REDSTONE_ORE, 60, -64, -1);
        add(entries, Blocks.LAPIS_ORE, 12, 0, 31);
        add(entries, Blocks.DEEPSLATE_LAPIS_ORE, 15, -64, -1);
        add(entries, Blocks.DIAMOND_ORE, 4, 0, 15);
        add(entries, Blocks.DEEPSLATE_DIAMOND_ORE, 12, -64, -1);
        if (terrain == Terrain.MOUNTAIN) {
            add(entries, Blocks.EMERALD_ORE, 6, 0, 63);
            add(entries, Blocks.DEEPSLATE_EMERALD_ORE, 3, -16, -1);
        }
    }

    private static void nether(List<Entry> entries) {
        add(entries, Blocks.SOUL_SAND, 250, 0, 127);
        add(entries, Blocks.SOUL_SOIL, 150, 0, 127);
        add(entries, Blocks.GRAVEL, 150, 0, 127);
        add(entries, Blocks.BASALT, 250, 0, 127);
        add(entries, Blocks.BLACKSTONE, 250, 0, 127);
        add(entries, Blocks.MAGMA_BLOCK, 100, 0, 127);
        add(entries, Blocks.GLOWSTONE, 30, 64, 127);
        add(entries, Blocks.NETHER_QUARTZ_ORE, 150, 0, 127);
        add(entries, Blocks.NETHER_GOLD_ORE, 100, 0, 127);
        add(entries, Blocks.ANCIENT_DEBRIS, 2, 8, 119);
    }
}
