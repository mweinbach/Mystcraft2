package net.mystcraft.minecraft.age.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Classic default biome surface state machine, with explicit modern material mapping. */
final class ClassicNormalSurface {
    private ClassicNormalSurface() {}
    static void build(ChunkAccess chunk, int sea, double[] noise) {
        build(chunk,sea,noise,null);
    }
    static void build(ChunkAccess chunk,int sea,double[] noise,ClassicSpecializedSurface specialized) {
        var random = new LegacyRandomSource(chunk.getPos().x * 341873128712L + chunk.getPos().z * 132897987541L);
        var pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            var biome = chunk.getNoiseBiome((baseX + x) >> 2, 16, (baseZ + z) >> 2);
            var kind = specialized == null ? ClassicSurfaceRules.Kind.DEFAULT : biome.unwrapKey().map(key->ClassicSurfaceRules.kind(key.location().getNamespace(),key.location().getPath())).orElse(ClassicSurfaceRules.Kind.DEFAULT);
            double columnNoise=noise[z+x*16];
            if(specialized!=null&&specialized.column(chunk,random,baseX+x,baseZ+z,sea,columnNoise,kind))continue;
            if(kind==ClassicSurfaceRules.Kind.SWAMP)specialized.swamp(chunk,baseX+x,baseZ+z);
            String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
            BlockState originalTop = Blocks.GRASS_BLOCK.defaultBlockState(), originalFiller = Blocks.DIRT.defaultBlockState();
            if (name.contains("desert") || name.equals("beach") || name.equals("snowy_beach")) originalTop = originalFiller = Blocks.SAND.defaultBlockState();
            else if (name.contains("badlands")) { originalTop = Blocks.RED_SAND.defaultBlockState(); originalFiller = Blocks.RED_SAND.defaultBlockState(); }
            else if (name.equals("mushroom_fields")) originalTop = Blocks.MYCELIUM.defaultBlockState();
            else if (name.equals("stony_shore")) originalTop = originalFiller = Blocks.STONE.defaultBlockState();
            else if (biome.is(BiomeTags.IS_NETHER)) originalTop = originalFiller = Blocks.NETHERRACK.defaultBlockState();
            else if (biome.is(BiomeTags.IS_END)) originalTop = originalFiller = Blocks.END_STONE.defaultBlockState();
            if(specialized!=null){
                var replacement=ClassicSurfaceRules.materials(kind,columnNoise);
                if(replacement!=null){originalTop=ClassicSpecializedSurface.material(replacement.top());originalFiller=ClassicSpecializedSurface.material(replacement.filler());}
            }
            BlockState top = originalTop, filler = originalFiller;
            int remaining = -1, thickness = (int) (noise[z + x * 16] / 3 + 3 + random.nextDouble() * 0.25);
            for (int y = 255; y >= 0; y--) {
                pos.set(baseX + x, y, baseZ + z);
                if (y <= random.nextInt(5)) { chunk.setBlockState(pos, Blocks.BEDROCK.defaultBlockState(), false); continue; }
                BlockState state = chunk.getBlockState(pos);
                if (state.isAir()) { remaining = -1; continue; }
                if (!state.is(Blocks.STONE)) continue;
                if (remaining == -1) {
                    if (thickness <= 0) { top = Blocks.AIR.defaultBlockState(); filler = Blocks.STONE.defaultBlockState(); }
                    else if (y >= sea - 4 && y <= sea + 1) { top = originalTop; filler = originalFiller; }
                    if (y < sea && top.isAir()) top = biome.value().coldEnoughToSnow(pos) ? Blocks.ICE.defaultBlockState() : Blocks.WATER.defaultBlockState();
                    remaining = thickness;
                    if (y >= sea - 1) chunk.setBlockState(pos, top, false);
                    else if (y < sea - 7 - thickness) {
                        top = Blocks.AIR.defaultBlockState(); filler = Blocks.STONE.defaultBlockState();
                        chunk.setBlockState(pos, Blocks.GRAVEL.defaultBlockState(), false);
                    } else chunk.setBlockState(pos, filler, false);
                } else if (remaining > 0) {
                    remaining--; chunk.setBlockState(pos, filler, false);
                    if (remaining == 0 && (filler.is(Blocks.SAND) || filler.is(Blocks.RED_SAND)) && thickness > 1) {
                        remaining = random.nextInt(4) + Math.max(0, y - 63);
                        filler = filler.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
                    }
                }
            }
        }
    }
}
