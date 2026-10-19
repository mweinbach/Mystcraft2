package net.mystcraft.minecraft.age.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;
import static net.mystcraft.minecraft.age.generation.ClassicSurfaceRules.Kind.*;
import static net.mystcraft.minecraft.age.generation.ClassicSurfaceRules.Material.*;

class ClassicSpecializedSurfaceTest {
    @Test void matchesIndependentExtractedNativeMesaColumnsAndRngContinuation() throws Exception {
        int cases=0;
        try(var reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/mystcraft/classic-mesa-golden.csv"),StandardCharsets.UTF_8))){
            for(String line:reader.lines().filter(s->!s.startsWith("#")&&!s.isBlank()).toList()){
                String[] p=line.split(",");long seed=Long.parseLong(p[0]);int type=Integer.parseInt(p[1]),x=Integer.parseInt(p[2]),z=Integer.parseInt(p[3]),sea=Integer.parseInt(p[4]),height=Integer.parseInt(p[6]);
                double noise=Double.parseDouble(p[5]);int[] column=new int[256];Arrays.fill(column,0,height+1,ClassicMesaSurface.STONE);
                var random=new LegacyRandomSource((x>>4)*341873128712L+(z>>4)*132897987541L);
                new ClassicMesaSurface(seed).column(new ClassicMesaSurface.Column(){public int get(int y){return column[y];}public void set(int y,int state){column[y]=state;}},random,x,z,sea,noise,type==1,type==2);
                var hash=MessageDigest.getInstance("SHA-256");for(int value:column)hash.update((byte)value);
                assertEquals(p[7],HexFormat.of().formatHex(hash.digest()),line);
                assertEquals(Long.parseLong(p[8]),random.nextLong(),"native column random continuation: "+line);cases++;
            }
        }
        assertEquals(120,cases);
    }
    @Test void matchesNativeSwampNoiseGoldensAndPoolOutcomes() throws Exception {
        var sampler=new ClassicSwampSurface();int pools=0,lilies=0,dry=0;
        try(var reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/mystcraft/classic-swamp-golden.csv"),StandardCharsets.UTF_8))){
            for(String line:reader.lines().filter(v->!v.startsWith("#")&&!v.isBlank()).toList()){
                String[] p=line.split(",");int x=Integer.parseInt(p[0]),z=Integer.parseInt(p[1]);double expected=Double.parseDouble(p[2]);
                assertEquals(expected,sampler.noise(x,z),0,line);
                int[] column=new int[256];Arrays.fill(column,0,63,ClassicMesaSurface.STONE);
                sampler.column(new ClassicSwampSurface.Column(){public int get(int y){return column[y];}public void set(int y,int state){column[y]=state;}},x,z);
                if(expected>0){assertEquals(ClassicSwampSurface.WATER,column[62],line);pools++;}
                else{assertEquals(ClassicMesaSurface.STONE,column[62],line);dry++;}
                if(expected>0&&expected<.12){assertEquals(ClassicSwampSurface.LILY,column[63],line);lilies++;}
                else assertEquals(ClassicSwampSurface.AIR,column[63],line);
            }
        }
        assertTrue(pools>0&&lilies>0&&dry>0,"goldens must cover all three source pool branches");
    }
    @Test void nativeTaigaAndHillsThresholdsIncludeExactEqualityAndWoodedException(){
        assertEquals(GRASS,ClassicSurfaceRules.materials(MEGA_TAIGA,-.95).top());
        assertEquals(PODZOL,ClassicSurfaceRules.materials(MEGA_TAIGA,Math.nextUp(-.95)).top());
        assertEquals(PODZOL,ClassicSurfaceRules.materials(MEGA_TAIGA,1.75).top());
        assertEquals(COARSE_DIRT,ClassicSurfaceRules.materials(MEGA_TAIGA,Math.nextUp(1.75)).top());
        assertEquals(GRAVEL,ClassicSurfaceRules.materials(MUTATED_HILLS,Math.nextDown(-1d)).top());
        assertEquals(GRASS,ClassicSurfaceRules.materials(MUTATED_HILLS,-1).top());
        assertEquals(STONE,ClassicSurfaceRules.materials(MUTATED_HILLS,2).top());
        assertEquals(GRAVEL,ClassicSurfaceRules.materials(MUTATED_HILLS,Math.nextUp(2d)).top());
        assertEquals(GRASS,ClassicSurfaceRules.materials(HILLS,1).top());
        assertEquals(STONE,ClassicSurfaceRules.materials(HILLS,Math.nextUp(1d)).top());
        assertEquals(GRASS,ClassicSurfaceRules.materials(WOODED_HILLS,8).top());
    }
    @Test void specializedModernMappingNeverGuessesModdedOrNewBiomeNames(){
        assertEquals(BRYCE,ClassicSurfaceRules.kind("minecraft","eroded_badlands"));
        assertEquals(MEGA_TAIGA,ClassicSurfaceRules.kind("minecraft","old_growth_spruce_taiga"));
        assertEquals(MUTATED_HILLS,ClassicSurfaceRules.kind("minecraft","windswept_gravelly_hills"));
        assertEquals(DEFAULT,ClassicSurfaceRules.kind("addon","eroded_badlands"));
        assertEquals(DEFAULT,ClassicSurfaceRules.kind("minecraft","mangrove_swamp"));
        assertNull(ClassicSurfaceRules.materials(DEFAULT,10));
    }
}
