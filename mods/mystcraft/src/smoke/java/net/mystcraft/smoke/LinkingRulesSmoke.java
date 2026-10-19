package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.linking.*;

/** Actual service/command travel, persisted player mixin data and shared login/respawn policy. */
public final class LinkingRulesSmoke {
    private LinkingRulesSmoke(){}
    public static List<String> run(MinecraftServer server,ServerLevel origin)throws Exception{
        List<String> passed=new ArrayList<>();ServerLevel target=server.getLevel(origin.dimension().equals(Level.NETHER)?Level.OVERWORLD:Level.NETHER);
        check(target!=null,"Rules smoke needs second actual dimension");
        LinkPermissions permissions=LinkPermissions.get(server);CompoundTag savedPermissions=((SavedData)permissions).save(new CompoundTag());
        Map<ServerLevel,Map<BlockPos,BlockState>> states=new LinkedHashMap<>();List<ServerPlayer> players=new ArrayList<>();
        ServerPlayer player=player(server,origin);origin.addNewPlayer(player);players.add(player);String name=player.getGameProfile().getName();
        LinkingRulesConfig config=LinkingRulesConfig.get(server);
        try{
            pad(origin,new BlockPos(400,200,400),states);pad(target,new BlockPos(416,200,416),states);
            ItemStack book=new ItemStack(MystcraftItems.LINKING_BOOK.get());new LinkDestination(target.dimension().location(),416.5,200,416.5,0,0).write(book);
            CompoundTag flags=new CompoundTag();flags.putBoolean(LinkModifiers.FOLLOWING,true);book.getOrCreateTag().put("Flags",flags);LinkingRules.stampBook(server,target.dimension(),book);
            player.setItemInHand(InteractionHand.MAIN_HAND,book);
            var commandSource=server.createCommandSourceStack().withEntity(player).withLevel(origin).withPermission(4).withSuppressedOutput();
            check(server.getCommands().performPrefixedCommand(commandSource,"myst-permissions "+name+" restrict entry "+target.dimension().location())==1,
                    "operator permission command must dispatch for registry-key destination");
            check(!LinkingService.link(player,InteractionHand.MAIN_HAND,book)&&player.getLevel()==origin&&player.getItemInHand(InteractionHand.MAIN_HAND)==book,
                    "entry restriction must deny service before held book/inventory travel mutations");
            permissions.change(name,true,LinkPermissions.Direction.ENTRY,null);permissions.change(name,false,LinkPermissions.Direction.DEPART,null);
            check(!LinkingService.link(player,InteractionHand.MAIN_HAND,book)&&player.getLevel()==origin,"departure whitelist-empty must deny service");
            permissions.change(name,false,LinkPermissions.Direction.ENTRY,null);
            check(server.getCommands().performPrefixedCommand(commandSource.withPermission(2),"tpx "+target.dimension().location()+" 416.5 200 416.5")==1
                    &&player.getLevel()==target&&player.getItemInHand(InteractionHand.MAIN_HAND)==book,
                    "explicit operator tpx must bypass permission restrictions without consuming held book");
            player.hasChangedDimension();
            int beforeCreate=net.mystcraft.minecraft.age.AgeManager.data(server).entries().size();
            check(server.getCommands().performPrefixedCommand(commandSource,"myst-create Permissions smoke")==1
                    &&net.mystcraft.minecraft.age.AgeManager.data(server).entries().size()==beforeCreate+1,
                    "source-named create command must reserve one actual canonical Age");
            var created=net.mystcraft.minecraft.age.AgeManager.data(server).entries().stream().max(java.util.Comparator.comparingLong(net.mystcraft.minecraft.age.AgeSavedData.Entry::number)).orElseThrow();
            check(server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY,created.dimensionId()))!=null,
                    "operator-created Age must have a real loaded ServerLevel");
            check(((DimensionIdentityCarrier)player).mystcraft$getDimensionIdentity().equals(LinkingRules.identity(server,target.dimension())),
                    "real dimension-change hook must store target canonical UUID");
            CompoundTag serialized=((Entity)player).saveWithoutId(new CompoundTag());
            check(serialized.hasUUID("MystcraftDimensionUUID"),"loader ServerPlayer save mixin must persist dimension UUID");
            ServerPlayer loaded=player(server,target);players.add(loaded);((Entity)loaded).load(serialized);
            check(((DimensionIdentityCarrier)loaded).mystcraft$getDimensionIdentity().equals(((DimensionIdentityCarrier)player).mystcraft$getDimensionIdentity()),
                    "loader ServerPlayer load mixin must restore canonical UUID");
            ServerPlayer respawnCopy=player(server,target);players.add(respawnCopy);respawnCopy.restoreFrom(player,true);
            check(((DimensionIdentityCarrier)respawnCopy).mystcraft$getDimensionIdentity().equals(((DimensionIdentityCarrier)player).mystcraft$getDimensionIdentity()),
                    "native restoreFrom must copy dimension identity through mixin");
            permissions.change(name,true,LinkPermissions.Direction.ENTRY,null);permissions.change(name,true,LinkPermissions.Direction.DEPART,null);
            player.teleportTo(origin,400.5,200,400.5,0,0);player.hasChangedDimension();
            book.getOrCreateTag().putString("TargetUUID",UUID.randomUUID().toString());
            check(!LinkingService.link(player,InteractionHand.MAIN_HAND,book)&&player.getLevel()==origin,"wrong book target UUID must reject before travel");
            book.getOrCreateTag().remove("TargetUUID");
            check(LinkingService.link(player,InteractionHand.MAIN_HAND,book)&&player.getLevel()==target,"legacy missing target UUID remains source-compatible allowed");
            player.hasChangedDimension();
            ((DimensionIdentityCarrier)player).mystcraft$setDimensionIdentity(UUID.randomUUID());
            LinkingRules.onLogin(player);
            check(player.getLevel()==LinkingRules.home(server)&&((DimensionIdentityCarrier)player).mystcraft$getDimensionIdentity().equals(LinkingRules.identity(server,player.level.dimension())),
                    "shared login mismatch policy must safely eject home and replace stale identity even under restrictions");
            passed.add("linking rules: real entry/depart denial, explicit operator permission bypass, missing-versus-mismatched book UUID, native dimension-change identity and safe login mismatch return");
            passed.add("linking identity: actual ServerPlayer save/load/restoreFrom mixins preserve dimension UUID");
            // Exercise the false respawn option without touching the operator's configuration file.
            java.lang.reflect.Field field=LinkingRulesConfig.class.getDeclaredField("CONFIGS");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<MinecraftServer,LinkingRulesConfig> configs=(Map<MinecraftServer,LinkingRulesConfig>)field.get(null);
            try{
                configs.put(server,new LinkingRulesConfig(false,config.requireUUID(),config.homeDimension()));
                ServerLevel age=null;for(ServerLevel level:server.getAllLevels())if(net.mystcraft.minecraft.instability.DecayPropagation.isAge(level)&&level!=LinkingRules.home(server)){age=level;break;}
                check(age!=null,"AgeSmoke must precede respawn policy check");
                configs.put(server,new LinkingRulesConfig(true,config.requireUUID(),config.homeDimension()));
                ServerPlayer priorAgePlayer=player(server,age);players.add(priorAgePlayer);
                ServerPlayer nativeDefault=player(server,LinkingRules.home(server));nativeDefault.getLevel().addNewPlayer(nativeDefault);players.add(nativeDefault);
                nativeDefault.restoreFrom(priorAgePlayer,false);
                check(age.dimension().equals(((DimensionIdentityCarrier)nativeDefault).mystcraft$getRespawnOrigin()),
                        "native restoreFrom must retain transient prior Age origin for default respawn behavior");
                var bedDimension=nativeDefault.getRespawnDimension();var bedPosition=nativeDefault.getRespawnPosition();
                LinkingRules.onRespawn(nativeDefault);
                check(nativeDefault.getLevel()==age && nativeDefault.getRespawnDimension().equals(bedDimension)
                        && java.util.Objects.equals(nativeDefault.getRespawnPosition(),bedPosition),
                        "enabled Age respawn must restore prior Age after native default-world selection without changing bed data");
                configs.put(server,new LinkingRulesConfig(false,config.requireUUID(),config.homeDimension()));
                ServerPlayer respawning=player(server,age);age.addNewPlayer(respawning);players.add(respawning);
                LinkingRules.onRespawn(respawning);
                check(respawning.getLevel()==LinkingRules.home(server),"disabled Age respawn shared policy must return to configured home");
            }finally{configs.put(server,config);}
            passed.add("linking respawn policy: enabled prior-Age and disabled home respawn policies preserve stored bed data through native restoreFrom");
            return passed;
        }finally{
            LinkPermissions restored=LinkPermissions.load(savedPermissions);((SavedData)restored).setDirty();server.overworld().getDataStorage().set("mystcraft-linking-permissions",restored);
            for(ServerPlayer candidate:players){candidate.getInventory().clearContent();if(candidate.getLevel().players().contains(candidate))candidate.getLevel().removePlayerImmediately(candidate,Entity.RemovalReason.DISCARDED);else ((Entity)candidate).discard();}
            for(var level:states.entrySet())for(var entry:level.getValue().entrySet())level.getKey().setBlock(entry.getKey(),entry.getValue(),3);
        }
    }
    private static ServerPlayer player(MinecraftServer server,ServerLevel level){
        ServerPlayer player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"SmokePermissions"),null);
        Connection connection=new Connection(PacketFlow.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketSendListener listener){}};
        player.connection=new ServerGamePacketListenerImpl(server,connection,player);player.setPos(400.5,200,400.5);initializeXaero(player);return player;
    }
    private static void initializeXaero(ServerPlayer player) {
        Class<?> data;
        try { data = Class.forName("xaero.lib.common.player.ServerPlayerData"); }
        catch (ClassNotFoundException absent) { return; }
        try {
            // Xaero's real login handler calls this public lazy initializer. Its clone hook
            // assumes login already initialized the old player and directly transfers that data.
            Object initialized = data.getMethod("get", ServerPlayer.class).invoke(null, player);
            Class<?> carrier = Class.forName("xaero.lib.common.player.IServerPlayer");
            check(initialized != null && carrier.getMethod("xaerolib_getData").invoke(player) == initialized,
                    "installed XaeroLib must initialize actual per-player data before native cloning");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot initialize installed XaeroLib player lifecycle", failure);
        }
    }
    private static void pad(ServerLevel level,BlockPos center,Map<ServerLevel,Map<BlockPos,BlockState>> snapshots){
        var states=snapshots.computeIfAbsent(level,key->new LinkedHashMap<>());
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-2,-1,-2),center.offset(2,3,2))){check(level.getBlockEntity(pos)==null,"Rules fixture cannot replace block entities");states.put(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,pos.getY()==center.getY()-1?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
