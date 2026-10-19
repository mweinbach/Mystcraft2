package net.mystcraft.integration.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** S2C-only visual profiles; no receive handler exists for client-authored profiles. */
public final class ForgeAgeVisualTransport implements AgeVisualNetwork.Transport {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("mystcraft", "age_visuals"), () -> "3", "3"::equals, "3"::equals);
    private ForgeAgeVisualTransport() {}
    public static void initialize() {
        CHANNEL.messageBuilder(AgeVisualNetwork.Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AgeVisualNetwork.Snapshot::encode).decoder(AgeVisualNetwork.Snapshot::decode)
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.mystcraft.minecraft.client.age.ForgeAgeVisualClient.receive(packet));
                    context.get().setPacketHandled(true);
                }).add();
        AgeVisualNetwork.initialize(new ForgeAgeVisualTransport());
        MinecraftForge.EVENT_BUS.addListener(ForgeAgeVisualTransport::login);
        MinecraftForge.EVENT_BUS.addListener(ForgeAgeVisualTransport::respawn);
        MinecraftForge.EVENT_BUS.addListener(ForgeAgeVisualTransport::dimension);
        MinecraftForge.EVENT_BUS.addListener(ForgeAgeVisualTransport::reload);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> net.mystcraft.minecraft.client.age.ForgeAgeVisualClient::initialize);
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer player) AgeVisualNetwork.syncAll(player); }
    private static void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer player) AgeVisualNetwork.syncCurrent(player); }
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) AgeVisualNetwork.syncCurrent(player); }
    private static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) AgeVisualNetwork.syncAll(event.getPlayer());
        else AgeVisualNetwork.syncAll(event.getPlayerList().getServer());
    }
    @Override public void sendToPlayer(ServerPlayer player, AgeVisualNetwork.Snapshot packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
