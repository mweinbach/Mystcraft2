package net.mystcraft.minecraft.age.generation;

import java.util.Arrays;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Native 1.12 Mesa surface algorithm, independent of modern block/biome registries. */
final class ClassicMesaSurface {
    static final int AIR=0,STONE=1,BEDROCK=2,WATER=3,CLAY=4,WHITE=5,ORANGE=6,YELLOW=7,BROWN=8,RED=9,SILVER=10,RED_SAND=11,COARSE_DIRT=12,GRASS=13,UNCOLORED_STAINED=14;
    interface Column { int get(int y); void set(int y,int state); }
    private final int[] bands=new int[64];
    private final Perlin offset;
    private final Perlin pillars,roof;
    ClassicMesaSurface(long seed){
        Arrays.fill(bands,CLAY);
        RandomSource random=new LegacyRandomSource(seed);
        offset=new Perlin(random,1);
        for(int i=0;i<64;i++){i+=random.nextInt(5)+1;if(i<64)bands[i]=ORANGE;}
        stripes(random,YELLOW,1);stripes(random,BROWN,2);stripes(random,RED,1);
        int count=random.nextInt(3)+3,index=0;
        for(int i=0;i<count;i++){
            index+=random.nextInt(16)+4;
            if(index<64){bands[index]=WHITE;
                if(index>1&&random.nextBoolean())bands[index-1]=SILVER;
                if(index<63&&random.nextBoolean())bands[index+1]=SILVER;
            }
        }
        // Native BiomeMesa seeds these from its previous singleton worldSeed (initially0).
        // Freeze fresh-instance behavior, rather than world visitation/thread history.
        RandomSource pillarRandom=new LegacyRandomSource(0);
        pillars=new Perlin(pillarRandom,4);roof=new Perlin(pillarRandom,1);
    }
    private void stripes(RandomSource random,int color,int minimum){
        int count=random.nextInt(4)+2;
        for(int i=0;i<count;i++){
            int length=random.nextInt(3)+minimum,start=random.nextInt(64);
            for(int j=0;start+j<64&&j<length;j++)bands[start+j]=color;
        }
    }
    int band(int x,int y){
        // Original getBand ignores Z and samples X on both axes.
        int shift=(int)Math.round(offset.value(x/512.0,x/512.0)*2);
        return bands[(y+shift+64)%64];
    }
    double pillarHeight(int x,int z,double noise){
        int swappedX=(x&-16)+(z&15),swappedZ=(z&-16)+(x&15);
        double amount=Math.min(Math.abs(noise),pillars.value(swappedX*.25,swappedZ*.25));
        if(amount<=0)return 0;
        double maximum=Math.ceil(Math.abs(roof.value(swappedX*.001953125,swappedZ*.001953125))*50)+14;
        return Math.min(amount*amount*2.5,maximum)+64;
    }
    void column(Column column,RandomSource random,int x,int z,int sea,double noise,boolean bryce,boolean forest){
        double pillar=bryce?pillarHeight(x,z,noise):0;
        int top=UNCOLORED_STAINED,filler=UNCOLORED_STAINED;
        int thickness=(int)(noise/3+3+random.nextDouble()*.25);
        boolean plain=Math.cos(noise/3*Math.PI)>0,redSand=false;
        int remaining=-1,stoneCount=0;
        for(int y=255;y>=0;y--){
            if(column.get(y)==AIR&&y<(int)pillar)column.set(y,STONE);
            if(y<=random.nextInt(5))column.set(y,BEDROCK);
            else if(stoneCount<15||bryce){
                int state=column.get(y);
                if(state==AIR)remaining=-1;
                else if(state==STONE){
                    if(remaining==-1){
                        redSand=false;
                        if(thickness<=0){top=AIR;filler=STONE;}
                        else if(y>=sea-4&&y<=sea+1){top=UNCOLORED_STAINED;filler=UNCOLORED_STAINED;}
                        if(y<sea&&top==AIR)top=WATER;
                        remaining=thickness+Math.max(0,y-sea);
                        if(y>=sea-1){
                            if(forest&&y>86+thickness*2)column.set(y,plain?COARSE_DIRT:GRASS);
                            else if(y>sea+3+thickness)column.set(y,y>=64&&y<=127?(plain?CLAY:band(x,y)):ORANGE);
                            else{column.set(y,RED_SAND);redSand=true;}
                        }else column.set(y,filler==UNCOLORED_STAINED?ORANGE:filler);
                    }else if(remaining>0){remaining--;column.set(y,redSand?ORANGE:band(x,y));}
                    stoneCount++;
                }
            }
        }
    }
    /** Old NoiseGeneratorPerlin.getValue: no simplex origin offsets and octave amplitude doubles. */
    static final class Perlin {
        private final ClassicPointSimplex[] levels;
        Perlin(RandomSource random,int count){levels=new ClassicPointSimplex[count];for(int i=0;i<count;i++)levels[i]=new ClassicPointSimplex(random);}
        double value(double x,double z){double result=0,frequency=1;for(var level:levels){result+=level.value(x*frequency,z*frequency)/frequency;frequency/=2;}return result;}
    }
}
