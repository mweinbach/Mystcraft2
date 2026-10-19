package net.mystcraft.minecraft.age.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.minecraft.writing.PageData;

/** Ordered classic modifier queues: biome LIFO; block newest matching category first, SEA before TERRAIN. */
public final class ClassicGenerationCompiler {
    public record Result(ClassicGenerationPlan plan,Set<Integer> consumed,List<String> errors,int danglingInstability,int controllerDuplicates){
        public Result{consumed=Set.copyOf(consumed);errors=List.copyOf(errors);}
    }
    private record Material(BlockState state,boolean terrain,boolean sea,boolean structure,boolean fluid,boolean gas,boolean crystal,int index){}
    private ClassicGenerationCompiler(){}
    public static Result compile(List<CompoundTag> pages,long seed){return compile(pages,seed,false);}
    public static Result compileExecution(List<CompoundTag> pages,long seed){return compile(pages,seed,true);}
    private static Result compile(List<CompoundTag> pages,long seed,boolean execution){
        var consumed=new HashSet<Integer>();var errors=new ArrayList<String>();var palette=new ArrayList<ResourceLocation>();
        var pendingBiomes=new ArrayList<ResourceLocation>();var blocks=new ArrayList<Material>();var alterations=new ArrayList<ClassicGenerationPlan.Alteration>();
        String controller="native",terrain="normal";boolean hasBiome=false,hasTerrain=false;Integer ground=null,level=null;int cost=0,duplicates=0;
        BlockState fill=Blocks.STONE.defaultBlockState(),sea=Blocks.WATER.defaultBlockState();
        Random seeds=new Random(seed);
        for(int i=0;i<pages.size();i++){
            CompoundTag page=pages.get(i);if(page==null||!page.contains("symbol",Tag.TAG_STRING))continue;
            String raw=page.getString("symbol");var parsed=PageData.identifier(raw);if(parsed.isEmpty()||PageData.isLinkPanel(page))continue;
            long symbolSeed=seeds.nextLong();String path=parsed.get().getPath();
            ResourceLocation biome=biome(page,parsed.get());
            if(biome!=null){pendingBiomes.add(biome);consumed.add(i);int height=(int)(ClassicBiomeProfiles.profile(biome).height()*64+64);ground=ground==null?height:(ground+height)/2;continue;}
            if(parsed.get().equals(new ResourceLocation("mystcraft:modclear"))){
                consumed.add(i);cost+=(pendingBiomes.size()*100+blocks.size()*50)/5;pendingBiomes.clear();blocks.clear();continue;
            }
            ClassicMaterials.Entry descriptor=ClassicMaterials.find(parsed.get().toString());
            if(descriptor!=null||parsed.get().equals(new ResourceLocation("mystcraft:material"))||(parsed.get().getNamespace().equals("mystcraft")&&path.startsWith("fluid/"))||(parsed.get().getNamespace().equals("mystcraft")&&path.equals("nosea"))){
                try{
                    BlockState state=path.equals("nosea")?Blocks.AIR.defaultBlockState():ClassicMaterials.state(page);
                    boolean isTerrain=descriptor!=null?descriptor.terrainRank()>0:page.getBoolean("Terrain");
                    boolean isSea=path.equals("nosea")|| (descriptor!=null?descriptor.seaRank()>0:page.getBoolean("Sea"));
                    if(path.startsWith("fluid/")) {
                        var fluid=Registry.FLUID.get(new ResourceLocation(page.getString("Fluid")));
                        var rule=net.mystcraft.minecraft.writing.SymbolTuning.fluid(fluid,net.mystcraft.minecraft.writing.DynamicSymbolPages.fluidDisplayName(fluid));
                        if(rule.blacklisted())throw new IllegalArgumentException("Fluid symbol blacklisted");
                        isSea=!rule.seaBanned();
                    }
                    blocks.add(0,new Material(state,isTerrain,isSea,descriptor!=null?descriptor.structureRank()>0:page.getBoolean("Structure"),descriptor!=null?descriptor.fluidRank()>0:page.getBoolean("Fluid")||page.getBoolean("FluidMaterial"),descriptor!=null?descriptor.gasRank()>0:page.getBoolean("Gas"),descriptor!=null?descriptor.crystalRank()>0:page.getBoolean("Crystal"),i));consumed.add(i);
                }catch(IllegalArgumentException bad){errors.add("invalid_material:"+raw+":"+bad.getMessage());consumed.add(i);}continue;
            }
            if(!parsed.get().getNamespace().equals("mystcraft"))continue;
            if(path.startsWith("biocon")&&List.of("native","single","grid","tiled","tiny","small","medium","large","huge").contains(path.substring(6))){
                consumed.add(i);if(hasBiome){if(!execution){errors.add("conflicting_biome_controller:"+raw);continue;}duplicates++;}
                hasBiome=true;controller=path.substring(6);palette.clear();
                if(!controller.equals("native")){
                    int count=controller.equals("single")?Math.min(1,pendingBiomes.size()):pendingBiomes.size();
                    for(int j=0;j<count;j++)palette.add(pendingBiomes.remove(pendingBiomes.size()-1));
                }continue;
            }
            if(path.startsWith("terrain")&&ClassicTerrainSpec.supports(path.substring(7))){
                consumed.add(i);if(hasTerrain){if(!execution){errors.add("conflicting_terrain:"+raw);continue;}duplicates++;}
                hasTerrain=true;terrain=path.substring(7);if(terrain.equals("nether"))level=level==null?32:(level+32)/2;else if(terrain.equals("end"))level=level==null?49:(level+49)/2;fill=Blocks.STONE.defaultBlockState();sea=Blocks.WATER.defaultBlockState();
                // Void consumes no materials in the original and rejects dangling material controls below.
                if(!terrain.equals("void")){
                    sea=terrain.equals("end")?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState();
                    Material water=pop(blocks,false);if(water!=null)sea=water.state();
                    Material stone=pop(blocks,true);if(stone!=null)fill=stone.state();
                }continue;
            }
            if(path.equals("termodspheres"))path="spheres";
            if(List.of("caves","ravines","spheres","tendrils","dungeons","obelisks","lakessurface","lakesdeep","genspikes","cryform","denseores","skylands","hugetrees","floatislands","mineshafts","netherfort","strongholds","villages").contains(path)){
                consumed.add(i);BlockState material=switch(path){case "spheres"->Blocks.COBBLESTONE.defaultBlockState();case "tendrils"->Blocks.OAK_LOG.defaultBlockState();case "lakessurface"->Blocks.WATER.defaultBlockState();case "lakesdeep"->Blocks.LAVA.defaultBlockState();case "genspikes","floatislands"->Blocks.STONE.defaultBlockState();case "cryform"->Registry.BLOCK.get(new ResourceLocation("mystcraft:crystal")).defaultBlockState();default->Blocks.OBSIDIAN.defaultBlockState();};
                if(path.equals("spheres")||path.equals("tendrils")||path.equals("obelisks")||path.equals("floatislands")||path.equals("genspikes")||path.equals("lakessurface")||path.equals("lakesdeep")||path.equals("cryform")){
                    for(int j=0;j<blocks.size();j++)if(path.equals("lakessurface")?blocks.get(j).fluid():path.equals("lakesdeep")?(blocks.get(j).fluid()||blocks.get(j).gas()):path.equals("cryform")?blocks.get(j).crystal():blocks.get(j).structure()){material=blocks.remove(j).state();break;}
                }
                CompoundTag materialTag=NbtUtils.writeBlockState(material);
                if(path.equals("floatislands")&&!pendingBiomes.isEmpty())materialTag.putString("Biome",pendingBiomes.remove(pendingBiomes.size()-1).toString());
                alterations.add(new ClassicGenerationPlan.Alteration(path,symbolSeed,materialTag));
            }
        }
        if(execution)cost+=pendingBiomes.size()*100+blocks.size()*50;
        else { if(!pendingBiomes.isEmpty())errors.add("dangling_biomes:"+pendingBiomes.size());
            if(!blocks.isEmpty())errors.add("dangling_materials:"+blocks.size()); }
        // New version4 adds specialized native surfaces; existing v1/v2/v3 specs stay frozen.
        var spec=new ClassicTerrainSpec(4,terrain,Registry.BLOCK.getKey(fill.getBlock()),Registry.BLOCK.getKey(sea.getBlock()),ground==null?64:Math.max(1,Math.min(255,ground)),level==null?63:level,!terrain.equals("end")&&!terrain.equals("void"));
        var plan=new ClassicGenerationPlan(controller,palette,spec,NbtUtils.writeBlockState(fill),NbtUtils.writeBlockState(sea),alterations,alterations.stream().anyMatch(a->a.kind().equals("dungeons")));
        return new Result(plan,consumed,errors,cost,duplicates);
    }
    private static Material pop(List<Material> blocks,boolean terrain){for(int j=0;j<blocks.size();j++){var b=blocks.get(j);if(terrain?b.terrain():b.sea())return blocks.remove(j);}return null;}
    private static ResourceLocation biome(CompoundTag page,ResourceLocation id){
        if(!id.getNamespace().equals("mystcraft"))return null;
        if(id.getPath().equals("biome"))return ResourceLocation.tryParse(page.getString("Biome"));
        if(!id.getPath().startsWith("biome/"))return null;
        String[] parts=id.getPath().substring(6).split("/",2);return parts.length==2?ResourceLocation.tryParse(parts[0]+":"+parts[1]):null;
    }
}
