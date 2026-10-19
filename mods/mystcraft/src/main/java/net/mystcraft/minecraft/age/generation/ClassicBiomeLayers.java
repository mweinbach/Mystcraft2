package net.mystcraft.minecraft.age.generation;

/** Source-derived 1.12 GenLayerBiomeMyst/ZoomMyst/Smooth pipeline. Values are palette indexes,
 * never numeric registry IDs. Integer overflow, seed salts and negative-coordinate shifts match classic. */
public final class ClassicBiomeLayers {
    private static final long MULT = 6364136223846793005L, ADD = 1442695040888963407L;
    private final Layer generation;
    public ClassicBiomeLayers(long seed, int paletteSize, int zoom) {
        if (paletteSize < 1 || zoom < 0 || zoom > 4) throw new IllegalArgumentException("Invalid biome layers");
        Layer layer = new RandomBiomes(seed, paletteSize);
        for (int i = 0; i < 2; i++) layer = new Zoom(seed, 1000L + i, layer);
        for (int i = 0; i < zoom; i++) layer = new Zoom(seed, 1000L + i, layer);
        generation = new Smooth(seed, layer);
    }
    public int generation(int quartX, int quartZ) { return generation.get(quartX, quartZ); }
    private static long mix(long value, long salt) { return value * (value * MULT + ADD) + salt; }
    private static final class Cursor {
        final long world; long chunk;
        Cursor(long world, int x, int z) {
            this.world = world; chunk = world;
            chunk = mix(chunk, x); chunk = mix(chunk, z); chunk = mix(chunk, x); chunk = mix(chunk, z);
        }
        int next(int bound) { int result = (int) ((chunk >> 24) % bound); if (result < 0) result += bound; chunk = mix(chunk, world); return result; }
        int choose(int... values) { return values[next(values.length)]; }
    }
    private abstract static class Layer {
        final long world;
        // Bounded synchronized cache: sampling can run concurrently on worldgen workers.
        final java.util.Map<Long,Integer> cache = new java.util.LinkedHashMap<>(256, .75F, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long,Integer> e) { return size() > 8192; }
        };
        Layer(long seed, long salt) {
            long base = salt; for (int i = 0; i < 3; i++) base = mix(base, salt);
            long value = seed; for (int i = 0; i < 3; i++) value = mix(value, base); world = value;
        }
        final int get(int x, int z) {
            long key = ((long)x << 32) ^ (z & 0xffffffffL);
            synchronized (cache) { Integer value = cache.get(key); if (value != null) return value; }
            int result = sample(x,z); synchronized(cache) { cache.put(key,result); } return result;
        }
        abstract int sample(int x, int z);
    }
    private static final class RandomBiomes extends Layer {
        final int count;
        RandomBiomes(long seed, int count) { super(seed,200L); this.count=count; }
        int sample(int x,int z) { return new Cursor(world,x,z).next(count); }
    }
    private static final class Zoom extends Layer {
        final Layer parent;
        Zoom(long seed,long salt,Layer parent) { super(seed,salt); this.parent=parent; }
        int sample(int x,int z) {
            int px=x>>1,pz=z>>1;
            int a=parent.get(px,pz),b=parent.get(px+1,pz),c=parent.get(px,pz+1),d=parent.get(px+1,pz+1);
            Cursor random=new Cursor(world,px<<1,pz<<1);
            int lower=random.choose(a,c), upper=random.choose(a,b), middle=mode(random,a,b,c,d);
            return (x&1)==0 ? ((z&1)==0?a:lower) : ((z&1)==0?upper:middle);
        }
        private static int mode(Cursor r,int a,int b,int c,int d) {
            if (b==c&&c==d) return b; if (a==b&&a==c) return a; if(a==b&&a==d)return a;
            if(a==c&&a==d)return a; if(a==b&&c!=d)return a; if(a==c&&b!=d)return a;
            if(a==d&&b!=c)return a; if(b==c&&a!=d)return b; if(b==d&&a!=c)return b;
            if(c==d&&a!=b)return c; return r.choose(a,b,c,d);
        }
    }
    private static final class Smooth extends Layer {
        final Layer parent;
        Smooth(long seed,Layer parent) { super(seed,1000L); this.parent=parent; }
        int sample(int x,int z) {
            int west=parent.get(x-1,z),east=parent.get(x+1,z),north=parent.get(x,z-1),south=parent.get(x,z+1),center=parent.get(x,z);
            if(west==east&&north==south)return new Cursor(world,x,z).next(2)==0?west:north;
            if(west==east)return west; if(north==south)return north; return center;
        }
    }
}
