package net.mystcraft.integration.platform;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.network.AgeVisualNetwork;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;

/** Loader services used by shared gameplay classes. Installed during loader initialization. */
public final class GameplayPlatform {
    public interface Backend {
        Packet<?> spawnPacket(Entity entity);
        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData);
        Entity transfer(Entity source, ServerLevel target, Vec3 position, float yaw, float pitch);
    }

    private static Backend backend;

    private GameplayPlatform() {}

    public static void install(Backend implementation) {
        backend = Objects.requireNonNull(implementation);
    }

    private static Backend backend() {
        if (backend == null) throw new IllegalStateException("Mystcraft gameplay platform is not initialized");
        return backend;
    }

    public static Packet<?> spawnPacket(Entity entity) { return backend().spawnPacket(entity); }

    /** Returns the actual destination entity (which can be a replacement), or null on veto. */
    public static Entity transfer(Entity source, ServerLevel target, Vec3 position, float yaw, float pitch) {
        if (source.level.isClientSide || source.isRemoved() || !target.getServer().isSameThread()) return null;
        Entity transferred;
        if (source instanceof ServerPlayer player) {
            AgeVisualNetwork.syncLevel(player, target);
            player.teleportTo(target, position.x, position.y, position.z, yaw, pitch);
            transferred = player;
        } else if (source.level == target) {
            source.teleportTo(position.x, position.y, position.z);
            source.setYRot(yaw); source.setXRot(pitch); transferred = source;
        } else transferred = backend().transfer(source, target, position, yaw, pitch);
        return transferred != null && !transferred.isRemoved() && transferred.level == target
                && transferred.position().distanceToSqr(position) < 1.0E-8 ? transferred : null;
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, BlockPos position) {
        backend().openMenu(player, provider, data -> data.writeBlockPos(position));
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        backend().openMenu(player, provider, openingData);
    }

    public static void openItemMenu(ServerPlayer player, MenuProvider provider, InteractionHand hand) {
        backend().openMenu(player, provider, data -> data.writeEnum(hand));
    }
}
