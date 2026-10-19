package net.mystcraft.minecraft.age.generation;

import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Native BiomeSwamp pre-surface pool pass, with fixed source y62 and seed2345 noise. */
final class ClassicSwampSurface {
    static final int AIR=0,WATER=3,LILY=15;
    interface Column { int get(int y); void set(int y,int state); }
    private final ClassicMesaSurface.Perlin grass=new ClassicMesaSurface.Perlin(new LegacyRandomSource(2345),1);
    double noise(int x,int z){return grass.value(x*.25,z*.25);}
    void column(Column column,int x,int z){
        double value=noise(x,z);
        if(value<=0)return;
        for(int y=255;y>=0;y--)if(column.get(y)!=AIR){
            if(y==62&&column.get(y)!=WATER){column.set(y,WATER);if(value<.12)column.set(y+1,LILY);}
            break;
        }
    }
}
