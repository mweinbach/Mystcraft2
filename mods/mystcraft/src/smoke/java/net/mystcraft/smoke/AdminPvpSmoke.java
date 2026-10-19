package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.AgeSavedData;

/** Actual command dispatcher permissions, dimension-local time and native attributed damage. */
public final class AdminPvpSmoke {
    private AdminPvpSmoke(){}
    public static List<String> run(MinecraftServer server,ServerLevel origin){
        AgeSavedData.Entry entry=null;ServerLevel age=null;
        for(var candidate:AgeManager.data(server).entries()){
            ServerLevel loaded=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY,candidate.dimensionId()));
            if(loaded!=null){entry=candidate;age=loaded;break;}
        }
        check(entry!=null && age!=null,"Admin/PvP requires existing genuine Age");
        List<String> passed=new ArrayList<>();long oldTime=age.getDayTime(),overworldTime=server.overworld().getDayTime();
        boolean hadPvp=entry.state().contains("MystcraftPvp"),oldPvp=entry.state().getBoolean("MystcraftPvp");
        var oldInstability=entry.state().get("Instability");oldInstability=oldInstability==null?null:oldInstability.copy();
        boolean hadVersion=entry.state().contains("ClassicRuntimeVersion");int oldVersion=entry.state().getInt("ClassicRuntimeVersion");
        ServerPlayer attacker=player(server,age,"SmokePvpAttacker"),victim=player(server,age,"SmokePvpVictim");
        try {
            var source=server.createCommandSourceStack().withLevel(age).withEntity(attacker).withPermission(2).withSuppressedOutput();
            check(server.getCommands().performPrefixedCommand(source.withPermission(0),"mystcraft time set 1234")==0 && age.getDayTime()==oldTime,
                    "nonoperator must not execute namespaced administration");
            check(server.getCommands().performPrefixedCommand(source,"mystcraft time set 1234")==1 && age.getDayTime()==1234
                    && server.overworld().getDayTime()==overworldTime,"namespaced time must affect selected Age only");
            check(server.getCommands().performPrefixedCommand(source,"mystcraft time add 17")==1 && age.getDayTime()==1251,
                    "namespaced time add must use current dimension");
            check(server.getCommands().getDispatcher().getRoot().getChild("time")!=null,"vanilla time command must remain registered");
            check(server.getCommands().performPrefixedCommand(source,"mystcraft instability disable")==1
                    && !entry.state().getCompound("Instability").getBoolean("Enabled"),"operator disable must persist automatic runtime switch");
            check(server.getCommands().performPrefixedCommand(source,"mystcraft instability enable")==1
                    && entry.state().getCompound("Instability").getBoolean("Enabled"),"operator enable must persist runtime switch");
            int ages=AgeManager.data(server).entries().size();
            check(server.getCommands().performPrefixedCommand(source,"mystcraft book")==1,"operator recovery must provide a real descriptive book");
            ItemStack recovered=attacker.getInventory().getItem(0);
            check(AgeBookData.ageId(recovered).orElseThrow().equals(entry.id()) && AgeManager.prepare(attacker,recovered)
                    && AgeManager.data(server).entries().size()==ages,"recovered book must resolve existing identity without allocating new Age");
            passed.add("administration: real dispatcher rejects nonoperators, edits only selected Age time, retains vanilla time and recovers existing book identity; runtime toggle persists");
            // Expire genuine native spawn protection, rather than bypassing the damage implementation.
            for(int tick=0;tick<61;tick++)victim.tick();
            entry.state().putBoolean("MystcraftPvp",true);
            float controlHealth = victim.getHealth();
            boolean controlHurt = victim.hurt(DamageSource.GENERIC, 1);
            check(controlHurt && victim.getHealth() < controlHealth,
                    "environmental positive control before anti-PvP must damage fixture: accepted=" + controlHurt
                            + ", health=" + controlHealth + "->" + victim.getHealth()
                            + ", nativeInts=" + nativePlayerInts(victim));
            settleDamage(victim); victim.setHealth(20);
            if (server.isPvpAllowed()) {
                boolean pvpControl = victim.hurt(DamageSource.playerAttack(attacker), 1);
                check(pvpControl && victim.getHealth() < 20,
                        "enabled-PvP positive control must damage fixture: accepted=" + pvpControl
                                + ", health=" + victim.getHealth() + ", nativeInts=" + nativePlayerInts(victim));
                settleDamage(victim); victim.setHealth(20);
            }
            settleDamage(victim);
            entry.state().putBoolean("MystcraftPvp",false);
            check(!victim.hurt(DamageSource.playerAttack(attacker),1) && victim.getHealth()==20,
                    "anti-PvP Age must reject player-attributed melee damage");
            settleDamage(victim);
            net.minecraft.world.entity.projectile.Arrow arrow=new net.minecraft.world.entity.projectile.Arrow(age,attacker);
            check(!victim.hurt(DamageSource.arrow(arrow,attacker),1) && victim.getHealth()==20,
                    "anti-PvP Age must reject projectile damage attributed to player");
            settleDamage(victim);
            float beforeEnvironmental = victim.getHealth();
            boolean environmentalAccepted = victim.hurt(DamageSource.GENERIC,1);
            check(environmentalAccepted && victim.getHealth()<beforeEnvironmental,
                    "anti-PvP must still allow actual environmental damage after spawn protection: accepted="
                            + environmentalAccepted + ", health=" + beforeEnvironmental + "->" + victim.getHealth()
                            + ", absorption=" + victim.getAbsorptionAmount() + ", hurtWindow=" + victim.invulnerableTime
                            + ", invulnerable=" + victim.isInvulnerableTo(DamageSource.GENERIC));
            if(server.isPvpAllowed()){
                entry.state().putBoolean("MystcraftPvp",true);settleDamage(victim);victim.setHealth(20);
                check(victim.hurt(DamageSource.playerAttack(attacker),1) && victim.getHealth()<20,"ordinary Age must retain native enabled PvP");
            }
            passed.add("anti-PvP: native melee/projectile player damage rejects in authored Age while environmental damage remains effective");
            return passed;
        } finally {
            age.setDayTime(oldTime);if(hadPvp)entry.state().putBoolean("MystcraftPvp",oldPvp);else entry.state().remove("MystcraftPvp");
            if(oldInstability==null)entry.state().remove("Instability");else entry.state().put("Instability",oldInstability);
            if(hadVersion)entry.state().putInt("ClassicRuntimeVersion",oldVersion);else entry.state().remove("ClassicRuntimeVersion");
            net.mystcraft.minecraft.instability.InstabilityRuntime.clear(server);
            ((net.minecraft.world.level.saveddata.SavedData) AgeManager.data(server)).setDirty();attacker.getInventory().clearContent();
            ((Entity)attacker).discard();((Entity)victim).discard();
        }
    }
    private static ServerPlayer player(MinecraftServer server,ServerLevel level,String name){
        ServerPlayer player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),name),null);
        Connection connection=new Connection(PacketFlow.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketSendListener listener){}};
        player.connection=new ServerGamePacketListenerImpl(server,connection,player);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(32.5,220,32.5);player.setNoGravity(true);initializeOrigins(player);return player;
    }
    private static void settleDamage(ServerPlayer player) {
        // Real ticks expire native hurt timing and IndRev's independent ten-tick damage window.
        // IndRev updates its window before our PvP veto, so rejected attempts must also be separated.
        for (int tick = 0; tick < 21; tick++) player.tick();
    }

    private static String nativePlayerInts(ServerPlayer player) {
        java.util.Map<String, Integer> values = new java.util.LinkedHashMap<>();
        try {
            for (var field : ServerPlayer.class.getDeclaredFields()) {
                if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true); values.put(field.getName(), field.getInt(player));
                }
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return values.toString();
    }

    /** Complete the pack's real selection lifecycle; unchosen Origins players are intentionally invulnerable. */
    private static void initializeOrigins(ServerPlayer player) {
        Class<?> components;
        try { components = Class.forName("io.github.apace100.origins.registry.ModComponents"); }
        catch (ClassNotFoundException absent) { return; }
        try {
            Object key = components.getField("ORIGIN").get(null);
            Object component = Class.forName("dev.onyxstudios.cca.api.v3.component.ComponentKey")
                    .getMethod("get", Object.class).invoke(key, player);
            Class<?> api = Class.forName("io.github.apace100.origins.component.OriginComponent");
            Class<?> layerType = Class.forName("io.github.apace100.origins.origin.OriginLayer");
            Class<?> originType = Class.forName("io.github.apace100.origins.origin.Origin");
            Class<?> registry = Class.forName("io.github.apace100.origins.origin.OriginRegistry");
            var getOrigin = registry.getMethod("get", net.minecraft.resources.ResourceLocation.class);
            var layers = (java.util.Collection<?>) Class.forName("io.github.apace100.origins.origin.OriginLayers")
                    .getMethod("getLayers").invoke(null);
            for (Object layer : layers) {
                if (!(boolean) layerType.getMethod("isEnabled").invoke(layer)) continue;
                var options = (java.util.List<?>) layerType.getMethod("getOrigins", net.minecraft.world.entity.player.Player.class)
                        .invoke(layer, player);
                if (options.isEmpty()) continue;
                Object selected = options.stream().filter(id -> id.toString().equals("origins:human"))
                        .findFirst().orElse(null);
                if (selected == null) selected = options.get(0);
                Object origin = getOrigin.invoke(null, selected);
                check(origin != null, "Origins fixture selection must resolve a registered origin: " + selected);
                api.getMethod("setOrigin", layerType, originType).invoke(component, layer, origin);
            }
            check((boolean) api.getMethod("hasAllOrigins").invoke(component),
                    "Origins fixture must complete actual selection before damage assertions");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not initialize installed Origins fixture lifecycle", error);
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
