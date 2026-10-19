package net.mystcraft.minecraft.age.generation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassicBiomeLayersTest {
    @Test void matchesIndependentRectangularClassicLayerOracle(){
        // Independently extracted rectangular source sampler, retained in scripts/classic_biome_oracle.py.
        int[][] coords={{-513,-129},{-17,31},{-1,-1},{0,0},{1,1},{16,-16},{127,73},{4096,-8192}};
        int[][] expected={{1,5,1,1,1,4,1,2},{2,2,1,1,1,2,4,1},{4,5,1,1,1,3,1,6},{1,1,1,1,1,4,2,1},{2,1,1,1,1,1,4,4}};
        for(int zoom=0;zoom<5;zoom++){
            var layers=new ClassicBiomeLayers(123456789L,7,zoom);
            for(int sample=0;sample<coords.length;sample++)assertEquals(expected[zoom][sample],layers.generation(coords[sample][0],coords[sample][1]),"zoom="+zoom+" sample="+sample);
        }
    }
    @Test void independentSamplingOrderAndNegativeCoordinatesRemainStable(){
        var a=new ClassicBiomeLayers(-12345L,5,3);var b=new ClassicBiomeLayers(-12345L,5,3);
        for(int x=-40;x<40;x++)for(int z=-40;z<40;z++)a.generation(x,z);
        for(int x=39;x>=-40;x--)for(int z=39;z>=-40;z--)assertEquals(a.generation(x,z),b.generation(x,z));
    }
    @Test void materialMetadataTranslationsAndCategoryRanksFollowPinnedDescriptors(){
        assertEquals("minecraft:polished_granite",ClassicMaterials.find("mystcraft:modmat_stone_2").block().toString());
        assertEquals(5,ClassicMaterials.find("mystcraft:modmat_stone_2").terrainRank());
        assertEquals(3,ClassicMaterials.find("mystcraft:modmat_packed_ice_0").seaRank());
        assertEquals(4,ClassicMaterials.find("mystcraft:modmat_blockcrystal_0").crystalRank());
        assertEquals("minecraft:dark_oak_log",ClassicMaterials.find("mystcraft:modmat_log2_1").block().toString());
    }
}
