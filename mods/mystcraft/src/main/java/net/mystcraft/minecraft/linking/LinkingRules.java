package net.mystcraft.minecraft.linking;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.instability.DecayPropagation;

/** Shared classic permission/UUID/home policy; all mutations run on the server thread. */
public final class LinkingRules {
    private LinkingRules(){}
    public static UUID identity(MinecraftServer server,ResourceKey<Level> dimension){
        for(var age:AgeManager.data(server).entries())if(age.dimensionId().equals(dimension.location()))return age.id();
        return server.getLevel(dimension)==null?null:DimensionIdentityData.get(server).identity(dimension.location());
    }
    public static boolean dead(MinecraftServer server,ResourceKey<Level> dimension){
        if(DimensionIdentityData.get(server).dead(dimension.location()))return true;
        ServerLevel level=server.getLevel(dimension);
        if(level==null)return true;
        if(DecayPropagation.isAge(level))return AgeManager.data(server).entries().stream().noneMatch(age->age.dimensionId().equals(dimension.location()));
        return false;
    }
    public static void stampBook(MinecraftServer server,ResourceKey<Level> dimension,net.minecraft.world.item.ItemStack book){
        UUID identity=identity(server,dimension);if(identity!=null)book.getOrCreateTag().putString("TargetUUID",identity.toString());
    }
    public static boolean matchesBook(MinecraftServer server,ResourceKey<Level> dimension,net.minecraft.world.item.ItemStack book){
        if(!book.hasTag()||!book.getTag().contains("TargetUUID"))return true;
        if(!book.getTag().contains("TargetUUID",8))return false;
        try{return UUID.fromString(book.getTag().getString("TargetUUID")).equals(identity(server,dimension));}catch(IllegalArgumentException invalid){return false;}
    }
    public static boolean allows(ServerPlayer player,ResourceKey<Level> target,boolean operatorTravel){
        if(operatorTravel)return true;
        return !dead(player.server,target) && LinkPermissions.get(player.server).allows(player.getGameProfile().getName(),player.level.dimension().location(),target.location());
    }
    public static void onLogin(ServerPlayer player){
        requireServer(player);UUID saved=((DimensionIdentityCarrier)player).mystcraft$getDimensionIdentity();
        UUID expected=identity(player.server,player.level.dimension());
        if(dead(player.server,player.level.dimension()) || saved==null&&LinkingRulesConfig.get(player.server).requireUUID()
                || saved!=null&&!saved.equals(expected))player.server.execute(()->ejectHome(player));
        else onDimensionChanged(player);
    }
    public static void onDimensionChanged(ServerPlayer player){
        requireServer(player);
        if(dead(player.server,player.level.dimension())){player.server.execute(()->ejectHome(player));return;}
        ((DimensionIdentityCarrier)player).mystcraft$setDimensionIdentity(identity(player.server,player.level.dimension()));
    }
    public static void onRespawn(ServerPlayer player){
        requireServer(player);
        LinkingRulesConfig config=LinkingRulesConfig.get(player.server);
        DimensionIdentityCarrier carrier=(DimensionIdentityCarrier)player;
        ResourceKey<Level> previous=carrier.mystcraft$getRespawnOrigin();carrier.mystcraft$setRespawnOrigin(null);
        if(config.respawnInAges() && previous!=null && !previous.equals(player.level.dimension())) {
            ServerLevel prior=player.server.getLevel(previous);
            if(prior!=null && DecayPropagation.isAge(prior)) {
                Vec3 spawn=dead(player.server,previous)?null:safeSpawn(prior,player);
                if(spawn!=null && GameplayPlatform.transfer(player,prior,spawn,player.getYRot(),player.getXRot())==player) {
                    player.setDeltaMovement(0,0,0);player.fallDistance=0;onDimensionChanged(player);return;
                }
                ejectHome(player);return;
            }
        }
        if(!config.respawnInAges()&&DecayPropagation.isAge(player.getLevel()))player.server.execute(()->ejectHome(player));
        else onDimensionChanged(player);
    }
    public static ServerLevel home(MinecraftServer server){
        ResourceLocation id=LinkingRulesConfig.get(server).homeDimension();ResourceKey<Level> key=ResourceKey.create(Registry.DIMENSION_REGISTRY,id);
        ServerLevel level=AgeManager.ensureLoaded(server,key);
        if(level==null||dead(server,key))throw new IllegalStateException("Mystcraft home dimension is missing or marked dead: "+id);
        return level;
    }
    public static Vec3 safeSpawn(ServerLevel level,Entity entity){
        BlockPos spawn=level.getSharedSpawnPos();
        if(level.isOutsideBuildHeight(spawn))return null;
        level.getChunk(spawn.getX()>>4,spawn.getZ()>>4);
        for(int y=spawn.getY();y<level.getMaxBuildHeight();y++){
            Vec3 position=new Vec3(spawn.getX()+.5,y,spawn.getZ()+.5);
            AABB bounds=entity.getBoundingBox().move(position.subtract(entity.position()));
            if(bounds.maxY>level.getMaxBuildHeight())break;
            if(level.getWorldBorder().isWithinBounds(bounds)&&level.noCollision(entity,bounds)&&!level.containsAnyLiquid(bounds))return position;
        }
        return null;
    }
    public static boolean ejectHome(ServerPlayer player){
        requireServer(player);
        try{
            ServerLevel home=home(player.server);Vec3 position=safeSpawn(home,player);
            if(position==null)throw new IllegalStateException("Mystcraft home spawn has no safe arrival");
            Entity moved=GameplayPlatform.transfer(player,home,position,player.getYRot(),player.getXRot());
            if(moved!=player)throw new IllegalStateException("Mystcraft home return was vetoed");
            player.setDeltaMovement(0,0,0);player.fallDistance=0;
            ((DimensionIdentityCarrier)player).mystcraft$setDimensionIdentity(identity(player.server,home.dimension()));
            return true;
        }catch(RuntimeException error){
            com.mojang.logging.LogUtils.getLogger().error("Cannot return {} to configured Mystcraft home: {}",player.getGameProfile().getName(),error.toString());
            player.connection.disconnect(Component.literal("Mystcraft could not safely return you to the configured home dimension. Ask the server operator to check the home world and spawn."));
            return false;
        }
    }
    private static void requireServer(ServerPlayer player){if(!player.server.isSameThread())throw new IllegalStateException("Mystcraft linking policy requires the server thread");}
}
