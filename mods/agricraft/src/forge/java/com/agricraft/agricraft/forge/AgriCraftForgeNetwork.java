package com.agricraft.agricraft.forge;

import com.agricraft.agricraft.common.registry.AgriDataRegistries;
import com.agricraft.agricraft.client.ClientUtil;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Sends the server's plants, weeds, soils, mutations and fertilizers to clients. Upstream relies on
 * 1.20's synced datapack registries, which 1.19.2 lacks for mod registries.
 */
public final class AgriCraftForgeNetwork {

	private static final String PROTOCOL = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(AgriDataRegistries.SYNC_CHANNEL,
			() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	private AgriCraftForgeNetwork() {
	}

	public static void init() {
		CHANNEL.registerMessage(0, SyncRegistries.class, SyncRegistries::encode, SyncRegistries::decode,
				SyncRegistries::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	}

	public static void send(ServerPlayer player) {
		// The integrated server's own client already reads the server's registries.
		if (player.connection.connection.isMemoryConnection()) {
			return;
		}
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncRegistries(null));
	}

	/** Encodes the server's current registries; decoded on the client into {@code registries}. */
	public record SyncRegistries(Map<ResourceKey<?>, Registry<?>> registries) {

		static void encode(SyncRegistries message, FriendlyByteBuf buf) {
			AgriDataRegistries.write(buf);
		}

		static SyncRegistries decode(FriendlyByteBuf buf) {
			return new SyncRegistries(AgriDataRegistries.read(buf));
		}

		static void handle(SyncRegistries message, Supplier<NetworkEvent.Context> context) {
			context.get().enqueueWork(() -> {
				AgriDataRegistries.apply(message.registries());
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientUtil::refreshCreativeSearch);
			});
			context.get().setPacketHandled(true);
		}

	}

}
