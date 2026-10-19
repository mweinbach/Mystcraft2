package net.mystcraft.minecraft.instability;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.registry.DecayRegistry;

/** Source environmental algorithms with loaded-area bounds and persisted effect coordinate streams. */
public final class InstabilityEffects {
    private InstabilityEffects() {}
    public static void tick(ServerLevel level,LevelChunk chunk,int score,Map<String,Integer> effects,CompoundTag state) {
        for(var entry:effects.entrySet()) {
            String name=entry.getKey(); int count=Math.min(256,entry.getValue());
            if(potion(name)!=null) { potion(level,chunk,name,count); continue; }
            if(name.equals("burning")) { scorch(level,chunk,count); continue; }
            for(int copy=0;copy<count;copy++) {
                String key=name+":"+copy;
                if(name.equals("accelerated")) { extraTicks(level,chunk,state,key); continue; }
                if(name.equals("burning_explicit")) { scorch(level,chunk,1); continue; }
                if(name.equals("crumble")) { BlockPos pos=coordinates(level,chunk,state,key); crumble(level,pos); continue; }
                if(name.startsWith("decay")) {
                    if(level.random.nextInt(1000000)>=score) continue;
                    DecayType type=switch(name) { case "decayblue"->DecayType.BLUE;case "decaypurple"->DecayType.PURPLE;case "decayred"->DecayType.RED;case "decaywhite"->DecayType.WHITE;default->null; };
                    if(type!=null) seedDecay(level,coordinates(level,chunk,state,key),type);
                    continue;
                }
                if(name.equals("lightning") || name.equals("lightning_explicit")) {
                    boolean storm=level.isRaining() && level.isThundering() && level.random.nextInt(5000)==0;
                    if(!storm && level.random.nextInt(100000)!=0) continue;
                    BlockPos sample=coordinates(level,chunk,state,key);
                    BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,sample);
                    float red=.45F,green=.45F,blue=.5F;
                    var snapshots=state.getList("ExplicitLightning",10);
                    if(name.equals("lightning_explicit") && copy<snapshots.size()) {
                        var stops=net.mystcraft.minecraft.age.visual.AgeVisualSettings.readGradient(snapshots.getCompound(copy).getList("Stops",10));
                        if(!stops.isEmpty()) {
                            var color=new net.mystcraft.minecraft.age.visual.AgeVisualSettings.Provider("gradient",stops).color(level.getGameTime()/12000D);
                            red=color.red();green=color.green();blue=color.blue();
                        }
                    }
                    net.mystcraft.integration.registry.LightningRegistry.spawn(level,Vec3.atBottomCenterOf(pos),red,green,blue);
                } else if(name.equals("explosions") && level.random.nextInt(1000)==0) {
                    BlockPos pos=coordinates(level,chunk,state,key).above();
                    if(loadedRadius(level,pos,6)) level.explode(null,pos.getX(),pos.getY(),pos.getZ(),3,true,Explosion.BlockInteraction.DESTROY);
                } else if(name.equals("meteors") && level.random.nextInt(50000)==0) {
                    BlockPos pos=coordinates(level,chunk,state,key);
                    InstabilityRuntime.meteor(level,new Vec3(pos.getX(),500,pos.getZ()));
                }
            }
        }
    }
    public static BlockPos coordinates(ServerLevel level,LevelChunk chunk,CompoundTag state,String key) {
        CompoundTag streams=state.getCompound("LCG");
        int value=streams.contains(key) ? streams.getInt(key) : new java.util.Random(level.getSeed()^key.hashCode()).nextInt();
        value=value*3+1013904223; streams.putInt(key,value); state.put("LCG",streams); int bits=value>>2;
        return new BlockPos(chunk.getPos().getMinBlockX()+(bits&15),(bits>>16)&255,chunk.getPos().getMinBlockZ()+((bits>>8)&15));
    }
    public static boolean loadedRadius(ServerLevel level,BlockPos pos,int radius) {
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)
            for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++) if(!level.hasChunk(x,z)) return false;
        return true;
    }
    public static void seedDecay(ServerLevel level,BlockPos sample,DecayType type) {
        int min=type==DecayType.WHITE?20:25;
        int max=type==DecayType.PURPLE?54:level.getHeight(Heightmap.Types.MOTION_BLOCKING,sample.getX(),sample.getZ());
        if(max<=min) max=63;
        max=Math.min(256,max);
        if(max<min) return;
        BlockPos pos=new BlockPos(sample.getX(),min+sample.getY()%Math.max(1,max-min),sample.getZ());
        if(level.hasChunkAt(pos)) level.setBlock(pos,DecayRegistry.block(type).get().defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
    }
    private static Entity sampleEntity(ServerLevel level,LevelChunk chunk) {
        int section=level.random.nextInt(16);
        int x=chunk.getPos().getMinBlockX(),z=chunk.getPos().getMinBlockZ();
        List<Entity> entities=level.getEntities((Entity)null,new AABB(x,section*16,z,x+16,section*16+16,z+16),
                entity->entity.chunkPosition().equals(chunk.getPos()) && (entity.blockPosition().getY()>>4)==section);
        return entities.isEmpty()?null:entities.get(level.random.nextInt(entities.size()));
    }
    private static void scorch(ServerLevel level,LevelChunk chunk,int count) {
        if(level.random.nextInt(10)!=0) return;
        Entity entity=sampleEntity(level,chunk);
        if(entity!=null && level.canSeeSky(entity.blockPosition())) entity.setSecondsOnFire(4*count);
    }
    private static MobEffect potion(String name) {
        return switch(name.replace(",g","")) {
            case "blindness"->MobEffects.BLINDNESS;case "enemyregen"->MobEffects.REGENERATION;
            case "enemyresist"->MobEffects.DAMAGE_RESISTANCE;case "fatigue"->MobEffects.DIG_SLOWDOWN;
            case "hunger"->MobEffects.HUNGER;case "nausea"->MobEffects.CONFUSION;case "poison"->MobEffects.POISON;
            case "slow"->MobEffects.MOVEMENT_SLOWDOWN;case "weakness"->MobEffects.WEAKNESS;case "wither"->MobEffects.WITHER;default->null;
        };
    }
    private static void potion(ServerLevel level,LevelChunk chunk,String name,int count) {
        Entity entity=sampleEntity(level,chunk);
        if(!(entity instanceof LivingEntity living) || name.startsWith("enemy") && entity instanceof Player
                || !name.endsWith(",g") && !level.canSeeSky(entity.blockPosition())) return;
        int duration=name.startsWith("enemy")?200:name.startsWith("wither")?30:
                name.startsWith("blindness")||name.startsWith("nausea")?60:80;
        living.addEffect(new MobEffectInstance(potion(name),duration,count-1));
    }
    public static void extraTicks(ServerLevel level,LevelChunk chunk,CompoundTag state,String key) {
        for(int section=0;section<chunk.getSections().length;section++) {
            var storage=chunk.getSections()[section]; if(!storage.isRandomlyTickingBlocks()) continue;
            for(int attempt=0;attempt<3;attempt++) {
                BlockPos sample=coordinates(level,chunk,state,key);
                int y=level.getMinBuildHeight()+section*16+(sample.getY()&15);
                BlockPos pos=new BlockPos(sample.getX(),y,sample.getZ()); BlockState block=chunk.getBlockState(pos);
                if(block.isRandomlyTicking()) block.randomTick(level,pos,level.random);
            }
        }
    }
    public static boolean crumble(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return false;
        BlockState state=level.getBlockState(pos); Block block=state.getBlock(),target=null;
        if(block==Blocks.DIAMOND_ORE) target=Blocks.COAL_ORE;
        else if(block==Blocks.COAL_ORE || block==Blocks.IRON_ORE || block==Blocks.REDSTONE_ORE || block==Blocks.GOLD_ORE || block==Blocks.LAPIS_ORE || block==Blocks.STONE_BRICKS) target=Blocks.STONE;
        else if(block==Blocks.ICE) target=Blocks.WATER;
        else if(block==Blocks.GLOWSTONE || net.minecraft.core.Registry.BLOCK.getKey(block).toString().equals("mystcraft:crystal")) target=Blocks.GLASS;
        else if(block==Blocks.NETHER_BRICKS || block==Blocks.NETHER_QUARTZ_ORE) target=Blocks.NETHERRACK;
        else if(block==Blocks.NETHERRACK) target=Blocks.SOUL_SAND;
        else if(block==Blocks.SOUL_SAND || block==Blocks.STONE || block==Blocks.COBBLESTONE) target=Blocks.GRAVEL;
        else if(block==Blocks.GRASS_BLOCK || block==Blocks.MYCELIUM || block==Blocks.BROWN_MUSHROOM_BLOCK || block==Blocks.RED_MUSHROOM_BLOCK || block==Blocks.CLAY || state.is(net.minecraft.tags.BlockTags.PLANKS)) target=Blocks.DIRT;
        else if(block==Blocks.GRAVEL || block==Blocks.DIRT || block==Blocks.GLASS || block==Blocks.SANDSTONE) target=Blocks.SAND;
        else if(state.is(net.minecraft.tags.BlockTags.LOGS)) target=Blocks.OAK_PLANKS;
        else if(state.is(net.minecraft.tags.BlockTags.WOOL)) target=block==Blocks.WHITE_WOOL?Blocks.COBWEB:Blocks.WHITE_WOOL;
        else if(state.is(net.minecraft.tags.BlockTags.SAPLINGS) || state.is(net.minecraft.tags.BlockTags.LEAVES) || block==Blocks.COBWEB || block==Blocks.GRASS || block==Blocks.BROWN_MUSHROOM || block==Blocks.RED_MUSHROOM || state.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)) target=Blocks.AIR;
        return target!=null && level.setBlock(pos,target.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
    }
}
