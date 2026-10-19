package net.mystcraft.minecraft.age.generation;

/** Registry-independent 1.12 native surface override decisions and explicit modern identity mapping. */
final class ClassicSurfaceRules {
    enum Kind { DEFAULT, MESA, BRYCE, WOODED_MESA, MEGA_TAIGA, HILLS, MUTATED_HILLS, WOODED_HILLS, SWAMP }
    enum Material { GRASS, DIRT, COARSE_DIRT, PODZOL, GRAVEL, STONE }
    record Pair(Material top,Material filler){}
    static Kind kind(String namespace,String path){
        if(!namespace.equals("minecraft"))return Kind.DEFAULT;
        return switch(path){
            case "badlands"->Kind.MESA;case "eroded_badlands"->Kind.BRYCE;case "wooded_badlands"->Kind.WOODED_MESA;
            case "old_growth_pine_taiga","old_growth_spruce_taiga"->Kind.MEGA_TAIGA;
            case "windswept_hills"->Kind.HILLS;case "windswept_gravelly_hills"->Kind.MUTATED_HILLS;case "windswept_forest"->Kind.WOODED_HILLS;
            case "swamp"->Kind.SWAMP;default->Kind.DEFAULT;
        };
    }
    static Pair materials(Kind kind,double noise){
        if(kind==Kind.MEGA_TAIGA)return new Pair(noise>1.75?Material.COARSE_DIRT:noise>-.95?Material.PODZOL:Material.GRASS,Material.DIRT);
        if(kind==Kind.MUTATED_HILLS&&(noise< -1||noise>2))return new Pair(Material.GRAVEL,Material.GRAVEL);
        if((kind==Kind.HILLS||kind==Kind.MUTATED_HILLS)&&noise>1)return new Pair(Material.STONE,Material.STONE);
        if(kind==Kind.HILLS||kind==Kind.MUTATED_HILLS||kind==Kind.WOODED_HILLS)return new Pair(Material.GRASS,Material.DIRT);
        return null;
    }
    private ClassicSurfaceRules(){}
}
