package net.mystcraft.minecraft.age.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Classic ordered-palette controllers. Registry holders retain mod biome identity through codecs. */
public final class ClassicBiomeSource extends BiomeSource {
    public static final Codec<ClassicBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(s -> s.palette),
            Codec.STRING.fieldOf("controller").forGetter(s -> s.controller),
            Codec.LONG.fieldOf("seed").forGetter(s -> s.seed),
            BiomeSource.CODEC.optionalFieldOf("native_delegate").forGetter(s->s.delegate),
            Biome.CODEC.listOf().optionalFieldOf("additional_biomes",List.of()).forGetter(s->s.additional)
    ).apply(i, ClassicBiomeSource::new));
    private final List<Holder<Biome>> palette;
    private final String controller;
    private final long seed;
    private final ClassicBiomeLayers layers;
    private final java.util.Optional<BiomeSource> delegate;
    private final List<Holder<Biome>> additional;
    public ClassicBiomeSource(List<Holder<Biome>> palette,String controller,long seed) {
        this(palette,controller,seed,java.util.Optional.empty(),List.of());
    }
    public ClassicBiomeSource(List<Holder<Biome>> palette,String controller,long seed,java.util.Optional<BiomeSource> delegate,List<Holder<Biome>> additional) {
        super(java.util.stream.Stream.concat(java.util.stream.Stream.concat(palette.stream(),additional.stream()),delegate.stream().flatMap(s->s.possibleBiomes().stream())));
        this.delegate=delegate;this.additional=List.copyOf(additional);this.palette=List.copyOf(palette); this.controller=controller;this.seed=seed;
        if((palette.isEmpty()&&!controller.equals("native"))||palette.size()>256)throw new IllegalArgumentException("Invalid biome palette");
        int zoom=switch(controller){case "tiny"->0;case "small"->1;case "medium"->2;case "large"->3;case "huge"->4;default->-1;};
        if(zoom<0&&!List.of("native","single","grid","tiled").contains(controller))throw new IllegalArgumentException("Unknown biome controller");
        layers=zoom<0?null:new ClassicBiomeLayers(seed,palette.size(),zoom);
    }
    @Override protected Codec<? extends BiomeSource> codec(){return CODEC;}
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        if(delegate.isPresent())return delegate.get().getNoiseBiome(x,y,z,sampler);
        return palette.get(indexAtBlock(x*4,z*4));
    }
    public Holder<Biome> densityBiome(int quartX,int quartZ){
        if(delegate.isPresent())return delegate.get().getNoiseBiome(quartX,0,quartZ,Climate.empty());
        if(layers!=null)return palette.get(layers.generation(quartX,quartZ));
        // Tiled's original density accessor deliberately passes quart coordinates without *4.
        return palette.get(indexAtBlock(controller.equals("tiled")?quartX:quartX*4,controller.equals("tiled")?quartZ:quartZ*4));
    }
    public Holder<Biome> biome(net.minecraft.resources.ResourceLocation id){return possibleBiomes().stream().filter(h->h.unwrapKey().map(k->k.location().equals(id)).orElse(false)).findFirst().orElseThrow(()->new IllegalStateException("Missing frozen biome "+id));}
    public int indexAtBlock(int x,int z){
        if(controller.equals("single"))return 0;
        if(layers==null)return Math.floorMod((x>>4)+(z>>4),palette.size());
        // Classic VoronoiZoom samples four coarse corners after subtracting two blocks.
        int bx=x-2,bz=z-2,px=bx>>2,pz=bz>>2;
        double[] distances=new double[4];int[] indexes=new int[4];
        for(int dz=0;dz<=1;dz++)for(int dx=0;dx<=1;dx++){
            long world=voronoiSeed(seed),chunk=world;int cx=(px+dx)<<2,cz=(pz+dz)<<2;
            chunk=mix(chunk,cx);chunk=mix(chunk,cz);chunk=mix(chunk,cx);chunk=mix(chunk,cz);
            double jx=((chunk>>24)%1024+1024)%1024/1024.0;
            chunk=mix(chunk,world);
            double jz=((chunk>>24)%1024+1024)%1024/1024.0;
            double vx=(jx-.5)*3.6+dx*4-(bx&3),vz=(jz-.5)*3.6+dz*4-(bz&3);
            double distance=vx*vx+vz*vz;
            distances[dz*2+dx]=distance;indexes[dz*2+dx]=layers.generation(px+dx,pz+dz);
        }
        if(distances[0]<distances[1]&&distances[0]<distances[2]&&distances[0]<distances[3])return indexes[0];
        if(distances[1]<distances[0]&&distances[1]<distances[2]&&distances[1]<distances[3])return indexes[1];
        if(distances[2]<distances[0]&&distances[2]<distances[1]&&distances[2]<distances[3])return indexes[2];
        return indexes[3];
    }
    private static long mix(long value,long salt){return value*(value*6364136223846793005L+1442695040888963407L)+salt;}
    private static long voronoiSeed(long seed){long base=10;for(int i=0;i<3;i++)base=mix(base,10);for(int i=0;i<3;i++)seed=mix(seed,base);return seed;}
}
