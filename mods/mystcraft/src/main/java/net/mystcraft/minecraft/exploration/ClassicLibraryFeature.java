package net.mystcraft.minecraft.exploration;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.mystcraft.integration.registry.ExplorationRegistry;

/** Exact exported classic library block layout with modern world-generation boundaries. */
public final class ClassicLibraryFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceLocation LOOT = new ResourceLocation("mystcraft", "chests/small_library");
    private record Placement(int x, int y, int z, BlockState state) {}
    private static final int[][] DISPLAYS = {{6,2,2},{8,2,4},{8,2,5},{8,2,6},{6,2,8}};
    private static List<Placement> blueprint;
    public ClassicLibraryFeature() { super(NoneFeatureConfiguration.CODEC); }
    public static boolean candidate(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, 32), regionZ = Math.floorDiv(chunkZ, 32);
        Random random = new Random(regionX * 341873128712L + regionZ * 132897987541L + seed + 14357617L);
        return chunkX == regionX * 32 + random.nextInt(24) && chunkZ == regionZ * 32 + random.nextInt(24);
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ResourceLocation dimension = level.getLevel().dimension().location();
        if (!dimension.equals(new ResourceLocation("minecraft", "overworld"))
                && !(dimension.getNamespace().equals("mystcraft") && dimension.getPath().startsWith("age_"))) return false;
        int chunkX = context.origin().getX() >> 4, chunkZ = context.origin().getZ() >> 4;
        if (!candidate(level.getSeed(), chunkX, chunkZ)) return false;
        int x = chunkX * 16 + 2, z = chunkZ * 16 + 2;
        int total = 0, minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
        for (int dx : new int[]{0,5,10}) for (int dz : new int[]{0,5,10}) {
            int height = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + dx, z + dz);
            if (!level.getFluidState(new BlockPos(x + dx, height - 1, z + dz)).isEmpty()) return false;
            total += height; minimum = Math.min(minimum, height); maximum = Math.max(maximum, height);
        }
        // Conservative modern adaptation: no ocean towers or deep terrain excavation.
        if (maximum - minimum > 5) return false;
        BlockPos origin = new BlockPos(x, total / 9 - 1, z);
        if (origin.getY() < level.getMinBuildHeight() + 1 || origin.getY() + 11 >= level.getMaxBuildHeight()) return false;
        return generateAt(level, origin, context.random());
    }
    /** Shared placement path used by world generation and the opt-in runtime harness. */
    public boolean generateAt(WorldGenLevel level, BlockPos origin, net.minecraft.util.RandomSource random) {
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = 0; dy <= 10; dy++) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (!level.ensureCanWrite(pos) || level.getBlockEntity(pos) != null) return false;
        }
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        for (Placement placement : blueprint()) {
            level.setBlock(map(origin, placement.x(), placement.y(), placement.z(), rotation), placement.state().rotate(rotation), 2);
        }
        // Legacy fills air/liquid beneath the full footprint with cobblestone foundations.
        for (int dx = 0; dx < 11; dx++) for (int dz = 0; dz < 11; dz++) {
            for (int depth = 1; depth <= 12; depth++) {
                BlockPos pos = origin.offset(dx, -depth, dz);
                if (!level.ensureCanWrite(pos) || (!level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty())) break;
                level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        }
        BlockPos chest = map(origin, 4, 1, 2, rotation);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().rotate(rotation), 2);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity container) container.setLootTable(LOOT, random.nextLong());
        for (int[] location : DISPLAYS) {
            BlockPos pos = map(origin, location[0], location[1], location[2], rotation);
            level.setBlock(pos, ExplorationRegistry.LECTERN.get().defaultBlockState()
                    .setValue(MystLecternBlock.FACING, Direction.WEST).rotate(rotation), 2);
            if (level.getBlockEntity(pos) instanceof MystLecternBlockEntity lectern) lectern.setBook(ExplorationPages.sampleLectern(level.getLevel().registryAccess(), random));
        }
        return true;
    }
    public static BlockPos map(BlockPos origin, int x, int y, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> origin.offset(x, y, z);
            case CLOCKWISE_90 -> origin.offset(10 - z, y, x);
            case CLOCKWISE_180 -> origin.offset(10 - x, y, 10 - z);
            case COUNTERCLOCKWISE_90 -> origin.offset(z, y, 10 - x);
        };
    }
    private static synchronized List<Placement> blueprint() {
        if (blueprint != null) return blueprint;
        List<Placement> result = new ArrayList<>();
        try (var input = ClassicLibraryFeature.class.getResourceAsStream("/assets/mystcraft/exploration/small_library.json")) {
            if (input == null) throw new IllegalStateException("Missing classic library blueprint");
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var element : json.getAsJsonArray("placements")) {
                var data = element.getAsJsonObject();
                BlockState state = Registry.BLOCK.get(new ResourceLocation(data.get("block").getAsString())).defaultBlockState();
                for (var property : data.getAsJsonObject("properties").entrySet()) {
                    Property<?> definition = state.getBlock().getStateDefinition().getProperty(property.getKey());
                    if (definition == null) throw new IllegalStateException("Unknown library block property " + property.getKey());
                    state = setProperty(state, definition, property.getValue().getAsString());
                }
                var pos = data.getAsJsonArray("pos");
                result.add(new Placement(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt(), state));
            }
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        blueprint = List.copyOf(result); return blueprint;
    }
    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow());
    }
}
