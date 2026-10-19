package net.mystcraft.minecraft.age.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Explicit version-one surface adapter; 1.12 per-biome noisy surface builders are not claimed ported. */
final class ClassicSurface {
    private ClassicSurface() {}
    static void build(ChunkAccess chunk, BlockState terrain, int seaLevel) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int remaining = -1;
            for (int y = 255; y >= 0; y--) {
                pos.set(baseX + x, y, baseZ + z);
                BlockState state = chunk.getBlockState(pos);
                if (state.isAir()) { remaining = -1; continue; }
                if (!state.equals(terrain)) continue;
                Holder<Biome> biome = chunk.getNoiseBiome((baseX + x) >> 2, y >> 2, (baseZ + z) >> 2);
                String path = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
                BlockState top = Blocks.GRASS_BLOCK.defaultBlockState(), filler = Blocks.DIRT.defaultBlockState();
                if (biome.is(BiomeTags.IS_NETHER)) top = filler = Blocks.NETHERRACK.defaultBlockState();
                else if (biome.is(BiomeTags.IS_END)) top = filler = Blocks.END_STONE.defaultBlockState();
                else if (path.contains("desert") || path.contains("beach")) top = filler = Blocks.SAND.defaultBlockState();
                if (remaining == -1) {
                    remaining = 3;
                    chunk.setBlockState(pos, y >= seaLevel - 1 ? top : filler, false);
                } else if (remaining > 0) {
                    remaining--; chunk.setBlockState(pos, filler, false);
                }
            }
        }
    }
}
