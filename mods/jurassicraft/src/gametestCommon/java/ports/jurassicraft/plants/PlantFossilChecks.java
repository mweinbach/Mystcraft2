package ports.jurassicraft.plants;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Shared plant-fossil checks. Both loaders call these from their GameTest mods. */
public final class PlantFossilChecks {
    private PlantFossilChecks() {}

    public static void replaceTaggedCoal(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos marker = helper.absolutePos(new BlockPos(0, 2, 1));
        ChunkAccess chunk = level.getChunk(marker);
        int x0 = chunk.getPos().getMinBlockX() + 1;
        int z0 = chunk.getPos().getMinBlockZ() + 1;
        int y = marker.getY();
        Block[] coal = {Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.GILDED_BLACKSTONE};
        Block[] other = {Blocks.IRON_ORE, Blocks.DIAMOND_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE,
                Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.EMERALD_ORE};
        List<BlockPos> placedCoal = new ArrayList<>();
        for (int i = 0; i < coal.length; i++) {
            BlockPos pos = new BlockPos(x0 + i, y, z0);
            level.setBlock(pos, coal[i].defaultBlockState(), 2);
            placedCoal.add(pos);
            require(helper, CoalPlantFossilsFeature.isCoal(coal[i].defaultBlockState()), "tagged coal was not recognized: " + coal[i]);
        }
        List<BlockPos> placedOther = new ArrayList<>();
        for (int i = 0; i < other.length; i++) {
            BlockPos pos = new BlockPos(x0 + coal.length + i, y, z0);
            level.setBlock(pos, other[i].defaultBlockState(), 2);
            placedOther.add(pos);
            require(helper, !CoalPlantFossilsFeature.isCoal(other[i].defaultBlockState()), "non-coal was treated as coal: " + other[i]);
        }
        List<BlockPos> scanned = coalInScanOrder(chunk);
        for (int i = 0; i < placedCoal.size(); i++) {
            int at = scanned.indexOf(placedCoal.get(i));
            require(helper, at >= 0, "placed coal missing from the chunk scan");
            if (i > 0) require(helper, scanned.indexOf(placedCoal.get(i - 1)) < at, "coal scan left X order");
        }
        long seed = 8675309L;
        RandomSource expected = RandomSource.create(seed);
        List<Boolean> replaced = new ArrayList<>();
        for (BlockPos pos : scanned) {
            if (!level.ensureCanWrite(pos) || !CoalPlantFossilsFeature.isCoal(level.getBlockState(pos))) {
                replaced.add(false);
                continue;
            }
            replaced.add(expected.nextInt(10) == 0);
        }
        CoalPlantFossilsFeature.replaceChunk(level, chunk, RandomSource.create(seed));
        Block fossil = JurassicContent.PLANT_FOSSIL_BLOCK.get();
        for (int i = 0; i < scanned.size(); i++) {
            boolean isFossil = level.getBlockState(scanned.get(i)).is(fossil);
            require(helper, isFossil == replaced.get(i), "plant fossil did not follow the one-in-ten coal roll at " + scanned.get(i));
        }
        for (int i = 0; i < placedOther.size(); i++)
            require(helper, level.getBlockState(placedOther.get(i)).is(other[i]), "non-coal ore changed");
        helper.succeed();
    }

    public static void scheduledAfterOrdinaryOres(GameTestHelper helper) {
        var plains = helper.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS).value();
        var steps = plains.getGenerationSettings().features();
        ResourceLocation fossilId = new ResourceLocation(PortMod.MOD_ID, "coal_plant_fossils");
        ResourceLocation coalId = new ResourceLocation("minecraft", "ore_coal_upper");
        ResourceLocation freezeId = new ResourceLocation("minecraft", "freeze_top_layer");
        int ores = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        int top = GenerationStep.Decoration.TOP_LAYER_MODIFICATION.ordinal();
        require(helper, ores < top && steps.size() > top, "decoration steps are missing");
        int fossilHits = 0;
        int fossilIndex = -1;
        int freezeIndex = -1;
        boolean coalInOres = false;
        for (int step = 0; step < steps.size(); step++) {
            int index = 0;
            for (var holder : steps.get(step)) {
                ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
                if (fossilId.equals(id)) {
                    fossilHits++;
                    require(helper, step == top, "plant fossils are not in the last decoration step");
                    fossilIndex = index;
                }
                if (step == ores && coalId.equals(id)) coalInOres = true;
                if (step == top && freezeId.equals(id)) freezeIndex = index;
                index++;
            }
        }
        require(helper, fossilHits == 1, "plant fossils must be added once, found " + fossilHits);
        require(helper, coalInOres, "vanilla coal is not in the ore step");
        require(helper, fossilIndex > freezeIndex && freezeIndex >= 0, "plant fossils do not run after ordinary top-layer features");
        helper.succeed();
    }

    private static List<BlockPos> coalInScanOrder(ChunkAccess chunk) {
        List<BlockPos> found = new ArrayList<>();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.maybeHas(CoalPlantFossilsFeature::isCoal)) continue;
            int minY = section.bottomBlockY();
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (CoalPlantFossilsFeature.isCoal(section.getBlockState(x, y, z)))
                    found.add(new BlockPos(minX + x, minY + y, minZ + z));
            }
        }
        return found;
    }

    private static void require(GameTestHelper helper, boolean value, String message) {
        if (!value) helper.fail(message);
    }
}
