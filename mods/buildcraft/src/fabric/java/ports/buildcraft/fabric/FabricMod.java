package ports.buildcraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.GenerationStep;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.SchematicSingleItem;
import ports.buildcraft.core.BCCoreEvents;
import ports.buildcraft.platform.Platform;

/** Fabric entrypoint: installs the platform, registers the shared modules, exposes storages and adds oil wells. */
public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        BuildCraft.init();
        platform.flush();
        BlockEntityType<?>[] types = Registry.BLOCK_ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(BuildCraft.MOD_ID))
                .map(entry -> entry.getValue())
                .toArray(BlockEntityType<?>[]::new);
        FabricPorts.register(types);
        FabricShardStorage.register();
        BuildCraft.serverReceivers().forEach((channel, receiver) -> ServerPlayNetworking.registerGlobalReceiver(channel,
                (server, player, handler, buffer, sender) -> {
                    FriendlyByteBuf payload = new FriendlyByteBuf(buffer.copy());
                    server.execute(() -> {
                        try {
                            receiver.receive(player, payload);
                        } finally {
                            payload.release();
                        }
                    });
                }));
        UseBlockCallback.EVENT.register(SchematicSingleItem::useFirst);
        ServerTickEvents.END_WORLD_TICK.register(BCCoreEvents::onLevelTick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ServerPlayer player) BCCoreEvents.onPlayerJoinLevel(player, level);
        });
        // Forge adds the same placed feature to #minecraft:is_overworld through data/buildcraft/forge/biome_modifier/oil_well.json.
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld().or(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)),
                GenerationStep.Decoration.LAKES, ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, BuildCraft.id("oil_well")));
    }
}
