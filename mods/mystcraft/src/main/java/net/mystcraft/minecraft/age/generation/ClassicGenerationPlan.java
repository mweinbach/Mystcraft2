package net.mystcraft.minecraft.age.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.state.BlockState;

/** Frozen executable generation, separate from original book pages and compiler completion rules. */
public record ClassicGenerationPlan(String biomeController,List<ResourceLocation> biomes,ClassicTerrainSpec terrain,
                                    CompoundTag fillState,CompoundTag seaState,List<Alteration> alterations,boolean dungeons) {
    public static final String KEY="ClassicGeneration";
    public static final Codec<ClassicGenerationPlan> CODEC=CompoundTag.CODEC.xmap(ClassicGenerationPlan::fromTag,ClassicGenerationPlan::toTag);
    public record Alteration(String kind,long seed,CompoundTag material){
        public Alteration{material=material.copy();}
        public Alteration(String kind,long seed){this(kind,seed,new CompoundTag());}
        @Override public CompoundTag material(){return material.copy();}
        public BlockState state(){return ClassicMaterials.state(materialPage(material));}
    }
    public ClassicGenerationPlan {
        biomes=List.copyOf(biomes);alterations=List.copyOf(alterations);fillState=fillState.copy();seaState=seaState.copy();
        if(biomes.size()>256||alterations.size()>256||!List.of("native","single","grid","tiled","tiny","small","medium","large","huge").contains(biomeController))throw new IllegalArgumentException("Invalid generation plan");
        for(var a:alterations)if(!List.of("caves","ravines","spheres","tendrils","dungeons","obelisks","lakessurface","lakesdeep","genspikes","cryform","denseores","skylands","hugetrees","floatislands","mineshafts","netherfort","strongholds","villages").contains(a.kind()))throw new IllegalArgumentException("Unknown alteration");
    }
    @Override public CompoundTag fillState(){return fillState.copy();}
    @Override public CompoundTag seaState(){return seaState.copy();}
    /** Resolve random palette completion once before persistence, against the actual dynamic registry. */
    public ClassicGenerationPlan resolveBiomes(Registry<Biome> registry,long seed){
        var resolved=new ArrayList<>(biomes);int min=biomeController.equals("native")?0:biomeController.equals("single")?1:biomeController.equals("grid")||biomeController.equals("tiled")?2:3;
        var selectable=new ArrayList<ResourceLocation>();registry.forEach(value->selectable.add(registry.getKey(value)));
        if(selectable.isEmpty()&&min>0)throw new IllegalArgumentException("No available biomes");
        Random random=new Random(seed);while(resolved.size()<min)resolved.add(selectable.get(random.nextInt(selectable.size())));
        for(var id:resolved)if(!registry.containsKey(id))throw new IllegalArgumentException("Saved generation references missing biome "+id);
        var features=new ArrayList<Alteration>();
        for(var a:alterations){
            CompoundTag material=a.material();
            if(a.kind().equals("floatislands")){
                if(!material.contains("Biome",Tag.TAG_STRING))material.putString("Biome",selectable.get(new Random(a.seed()).nextInt(selectable.size())).toString());
                var id=new ResourceLocation(material.getString("Biome"));if(!registry.containsKey(id))throw new IllegalArgumentException("Missing island biome "+id);
            }
            features.add(new Alteration(a.kind(),a.seed(),material));
        }
        return new ClassicGenerationPlan(biomeController,resolved,terrain,fillState,seaState,features,dungeons);
    }
    public BiomeSource biomeSource(Registry<Biome> registry,long seed){
        var delegate=biomeController.equals("native")?java.util.Optional.<BiomeSource>of(MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(registry)):java.util.Optional.<BiomeSource>empty();
        var holders=biomes.stream().<net.minecraft.core.Holder<Biome>>map(id->registry.getHolderOrThrow(ResourceKey.create(Registry.BIOME_REGISTRY,id))).toList();
        var additional=alterations.stream().filter(a->a.kind().equals("floatislands")).<net.minecraft.core.Holder<Biome>>map(a->registry.getHolderOrThrow(ResourceKey.create(Registry.BIOME_REGISTRY,new ResourceLocation(a.material().getString("Biome"))))).toList();
        return new ClassicBiomeSource(holders,biomeController,seed,delegate,additional);
    }
    public BlockState fill(){return ClassicMaterials.state(materialPage(fillState));}
    public BlockState sea(){return ClassicMaterials.state(materialPage(seaState));}
    private static CompoundTag materialPage(CompoundTag state){CompoundTag page=new CompoundTag();page.put("BlockState",state.copy());return page;}
    public CompoundTag toTag(){
        CompoundTag tag=new CompoundTag();tag.putInt("Version",1);tag.putString("BiomeController",biomeController);ListTag palette=new ListTag();biomes.forEach(id->palette.add(StringTag.valueOf(id.toString())));tag.put("Biomes",palette);
        tag.put("Terrain",terrain.toTag());tag.put("FillState",fillState.copy());tag.put("SeaState",seaState.copy());tag.putBoolean("Dungeons",dungeons);
        ListTag carving=new ListTag();for(var a:alterations){CompoundTag item=new CompoundTag();item.putString("Kind",a.kind());item.putLong("Seed",a.seed());item.put("Material",a.material());carving.add(item);}tag.put("Alterations",carving);return tag;
    }
    public static ClassicGenerationPlan fromTag(CompoundTag tag){
        if(tag.getInt("Version")!=1)throw new IllegalArgumentException("Unsupported frozen generation plan");
        var biomes=new ArrayList<ResourceLocation>();var list=tag.getList("Biomes",Tag.TAG_STRING);for(int i=0;i<list.size();i++)biomes.add(new ResourceLocation(list.getString(i)));
        var carving=new ArrayList<Alteration>();var changes=tag.getList("Alterations",Tag.TAG_COMPOUND);for(int i=0;i<changes.size();i++){var a=changes.getCompound(i);carving.add(new Alteration(a.getString("Kind"),a.getLong("Seed"),a.getCompound("Material")));}
        return new ClassicGenerationPlan(tag.getString("BiomeController"),biomes,ClassicTerrainSpec.fromTag(tag.getCompound("Terrain")),tag.getCompound("FillState"),tag.getCompound("SeaState"),carving,tag.getBoolean("Dungeons"));
    }
}
