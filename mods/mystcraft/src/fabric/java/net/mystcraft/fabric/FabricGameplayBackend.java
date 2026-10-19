package net.mystcraft.fabric;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.PortalInfo;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.mystcraft.integration.platform.GameplayPlatform;

public final class FabricGameplayBackend implements GameplayPlatform.Backend {
    @Override public Entity transfer(Entity source, ServerLevel target, Vec3 position, float yaw, float pitch) {
        return FabricDimensions.teleport(source, target, new PortalInfo(position, source.getDeltaMovement(), yaw, pitch));
    }
    @Override public Packet<?> spawnPacket(Entity entity) { return new ClientboundAddEntityPacket(entity); }
    @Override public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf data) {
                openingData.accept(data);
            }
            @Override public Component getDisplayName() { return provider.getDisplayName(); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player owner) {
                return provider.createMenu(id, inventory, owner);
            }
        });
    }
}
