package net.mystcraft.integration.platform;

import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;
import java.util.function.Function;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkHooks;

public final class ForgeGameplayPlatform implements GameplayPlatform.Backend {
    @Override
    public Entity transfer(Entity source, ServerLevel target, Vec3 position, float yaw, float pitch) {
        Entity moved;
        try {
            moved = source.changeDimension(target, new ITeleporter() {
            @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel destination,
                                                       Function<ServerLevel, PortalInfo> fallback) {
                return new PortalInfo(position, entity.getDeltaMovement(), yaw, pitch);
            }
            @Override public Entity placeEntity(Entity entity, ServerLevel origin, ServerLevel destination,
                                                 float suggestedYaw, Function<Boolean, Entity> reposition) {
                // Vanilla removes the source even when destination insertion is vetoed. Confirm
                // insertion first, so a region protection/spawn veto cannot destroy the traveler.
                Entity replacement = entity.getType().create(destination);
                if (replacement == null) throw new SpawnRejected();
                replacement.restoreFrom(entity);
                replacement.moveTo(position.x, position.y, position.z, yaw, pitch);
                replacement.setDeltaMovement(entity.getDeltaMovement());
                if (!destination.addFreshEntity(replacement)) {
                    replacement.discard(); throw new SpawnRejected();
                }
                return replacement;
            }
            @Override public boolean playTeleportSound(ServerPlayer player, ServerLevel origin, ServerLevel destination) { return false; }
            });
        } catch (SpawnRejected canceled) {
            // These two sections are pushed by Entity.changeDimension before placeEntity.
            source.level.getProfiler().pop(); source.level.getProfiler().pop();
            return null;
        }
        if (moved != null) { moved.setYRot(yaw); moved.setXRot(pitch); }
        return moved;
    }
    private static final class SpawnRejected extends RuntimeException {
        SpawnRejected() { super(null, null, false, false); }
    }
    @Override
    public Packet<?> spawnPacket(Entity entity) { return NetworkHooks.getEntitySpawningPacket(entity); }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        NetworkHooks.openScreen(player, provider, openingData);
    }
}
