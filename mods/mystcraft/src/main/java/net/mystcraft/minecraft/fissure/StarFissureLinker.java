package net.mystcraft.minecraft.fissure;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.integration.registry.StarFissureRegistry;

/** A natural/external home link: no book, disarm, following or other item modifier is consumed. */
public final class StarFissureLinker {
    private static final Map<Entity, Long> RETRY_AFTER = new WeakHashMap<>();
    private StarFissureLinker() {}

    public static boolean returnHome(Entity entity) {
        if (!(entity.level instanceof ServerLevel origin) || !entity.isAlive()
                || entity.isPassenger() || entity.isVehicle() || entity.isOnPortalCooldown()) return false;
        if (entity instanceof ServerPlayer player && (player.isSpectator() || player.isSleeping()
                || player.isChangingDimension())) return false;
        long now = origin.getGameTime();
        if (RETRY_AFTER.getOrDefault(entity, Long.MIN_VALUE) > now) return false;
        // Failed collision/veto checks cannot produce a per-tick link-event storm.
        RETRY_AFTER.put(entity, now + 20);
        ServerLevel target;
        try { target = net.mystcraft.minecraft.linking.LinkingRules.home(origin.getServer()); }
        catch (IllegalStateException invalidHome) {
            org.slf4j.LoggerFactory.getLogger(StarFissureLinker.class).warn("Star fissure home unavailable: {}", invalidHome.getMessage());
            return false;
        }
        if (target == origin || net.mystcraft.minecraft.linking.LinkingRules.dead(origin.getServer(), target.dimension())) return false;
        if (entity instanceof ServerPlayer player && !net.mystcraft.minecraft.linking.LinkingRules.allows(player, target.dimension(), false)) return false;
        Vec3 destination = safeSpawn(target, entity);
        if (destination == null) return false;
        Vec3 departure = entity.position();
        Entity transferred = GameplayPlatform.transfer(entity, target, destination, entity.getYRot(), entity.getXRot());
        if (transferred == null || transferred.level != target
                || transferred.position().distanceToSqr(destination) > 1.0E-8) return false;
        transferred.setDeltaMovement(0, .2, 0);
        transferred.fallDistance = 0;
        transferred.setPortalCooldown();
        if (transferred instanceof ServerPlayer player) {
            player.closeContainer();
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        var sound = transferred instanceof net.minecraft.world.entity.item.ItemEntity
                || transferred instanceof net.mystcraft.minecraft.entity.LinkBookEntity
                ? net.mystcraft.integration.registry.MystcraftSounds.POP.get() : StarFissureRegistry.LINK_SOUND.get();
        net.mystcraft.minecraft.linking.ClassicLinkEffects.emit(origin, departure, sound);
        net.mystcraft.minecraft.linking.ClassicLinkEffects.emit(target, destination, sound);
        return true;
    }

    /** Bounded source-style vertical escape from an obstructed home spawn; never clears blocks. */
    public static Vec3 safeSpawn(ServerLevel target, Entity entity) {
        BlockPos spawn = target.getSharedSpawnPos();
        target.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        int start = Math.max(target.getMinBuildHeight(), spawn.getY());
        int ceiling = Math.min(target.getMaxBuildHeight() - (int) Math.ceil(entity.getBbHeight()), start + 512);
        for (int y = start; y <= ceiling; y++) {
            Vec3 position = new Vec3(spawn.getX() + .5, y, spawn.getZ() + .5);
            AABB bounds = entity.getBoundingBox().move(position.subtract(entity.position()));
            if (target.getWorldBorder().isWithinBounds(bounds) && target.noCollision(entity, bounds)
                    && !target.containsAnyLiquid(bounds)) return position;
        }
        return null;
    }
}
