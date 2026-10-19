package net.mystcraft.minecraft.age.generation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Ordered ModSymbolsModifiers descriptors, with 1.12 metadata translated to current registry identities. */
public final class ClassicMaterials {
    public record Entry(String terminal,ResourceLocation block,int terrainRank,int seaRank,int structureRank,int solidRank) {
        public int organicRank(){return block.getPath().endsWith("_log")?1:0;}
        public int fluidRank(){return switch(block.getPath()){case "water"->1;case "lava"->2;case "ice","packed_ice"->3;default->0;};}
        public int crystalRank(){return switch(block.getPath()){case "ice","packed_ice","glass","snow_block","obsidian"->3;case "glowstone","nether_quartz_ore","crystal"->4;default->0;};}
        public int gasRank(){return 0;}
        public CompoundTag payload(){CompoundTag tag=new CompoundTag();tag.putString("symbol",terminal);return tag;}
    }
    private static final List<Entry> ENTRIES;
    static {
        var entries=new ArrayList<Entry>();
        add(entries,"dirt",0,"dirt",4,0,2,1);add(entries,"stone",0,"stone",1,0,2,1);
        add(entries,"stone",1,"granite",2,0,2,1);add(entries,"stone",3,"diorite",2,0,2,1);add(entries,"stone",5,"andesite",2,0,2,1);
        add(entries,"stone",2,"polished_granite",5,0,1,1);add(entries,"stone",4,"polished_diorite",5,0,1,1);add(entries,"stone",6,"polished_andesite",5,0,1,1);
        add(entries,"sandstone",0,"sandstone",2,0,1,1);add(entries,"netherrack",0,"netherrack",3,0,2,2);add(entries,"end_stone",0,"end_stone",4,0,3,3);
        add(entries,"nether_brick",0,"nether_bricks",0,0,2,2);
        String[] logs={"oak","spruce","birch","jungle","acacia","dark_oak"};
        for(int i=0;i<logs.length;i++)add(entries,i<4?"log":"log2",i<4?i:i-4,logs[i]+"_log",0,0,1,1);
        String[] ores={"diamond_ore","gold_ore","iron_ore","coal_ore","redstone_ore","lapis_ore","emerald_ore"};int[] rank={6,5,4,4,5,5,5};
        for(int i=0;i<ores.length;i++)add(entries,ores[i],0,ores[i],0,0,rank[i],rank[i]);
        add(entries,"ice",0,"ice",0,2,3,3);add(entries,"packed_ice",0,"packed_ice",3,3,3,3);
        add(entries,"glass",0,"glass",0,0,3,3);add(entries,"snow",0,"snow_block",0,0,3,3);
        add(entries,"obsidian",0,"obsidian",4,0,3,4);add(entries,"glowstone",0,"glowstone",0,0,4,4);
        add(entries,"quartz_ore",0,"nether_quartz_ore",0,0,4,4);
        entries.add(new Entry("mystcraft:modmat_blockcrystal_0",new ResourceLocation("mystcraft:crystal"),0,0,4,4));
        add(entries,"water",0,"water",0,1,0,0);add(entries,"lava",0,"lava",0,2,0,0);
        ENTRIES=List.copyOf(entries);
    }
    private static void add(List<Entry> list,String old,int meta,String current,int terrain,int sea,int structure,int solid){
        list.add(new Entry("mystcraft:modmat_"+old+"_"+meta,new ResourceLocation("minecraft",current),terrain,sea,structure,solid));
    }
    public static List<Entry> entries(){return ENTRIES;}
    public static Entry find(String terminal){return ENTRIES.stream().filter(e->e.terminal().equals(terminal)).findFirst().orElse(null);}
    /** Modern extension for mod-provided material pages: explicit states/categories avoid guessing from names. */
    public static BlockState state(CompoundTag page){
        Entry entry=find(net.mystcraft.minecraft.writing.PageData.identifier(page.getString("symbol")).map(Object::toString).orElse(""));
        CompoundTag encoded=entry==null?page.getCompound("BlockState"):new CompoundTag();
        ResourceLocation id=entry==null?ResourceLocation.tryParse(encoded.getString("Name")):entry.block();
        if(id==null||!Registry.BLOCK.containsKey(id))throw new IllegalArgumentException("Unknown material block");
        if(page.getString("symbol").startsWith("mystcraft:fluid/")){
            ResourceLocation fluidId=ResourceLocation.tryParse(page.getString("Fluid"));
            if(fluidId==null||!Registry.FLUID.containsKey(fluidId))throw new IllegalArgumentException("Missing material fluid");
            var fluid=Registry.FLUID.get(fluidId);var nativeState=fluid.defaultFluidState().createLegacyBlock();
            if(!Registry.BLOCK.getKey(nativeState.getBlock()).equals(id)||!fluid.isSource(fluid.defaultFluidState()))throw new IllegalArgumentException("Fluid block mismatch or flowing fluid");
        }
        BlockState state=Registry.BLOCK.get(id).defaultBlockState();CompoundTag properties=encoded.getCompound("Properties");
        for(String key:properties.getAllKeys()){
            var property=state.getBlock().getStateDefinition().getProperty(key);
            if(property==null)throw new IllegalArgumentException("Unknown material property "+key);
            state=set(state,property,properties.getString(key));
        }
        if(page.getString("symbol").startsWith("mystcraft:fluid/")){
            ResourceLocation fluidId=new ResourceLocation(page.getString("Fluid"));
            String terminal="mystcraft:fluid/"+fluidId.getNamespace()+"/"+fluidId.getPath();
            if(!terminal.equals(page.getString("symbol"))||!state.equals(Registry.FLUID.get(fluidId).defaultFluidState().createLegacyBlock()))throw new IllegalArgumentException("Fluid terminal/state mismatch");
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Invalid material property")));
    }
    private ClassicMaterials(){}
}
