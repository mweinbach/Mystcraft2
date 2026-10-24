/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.proxy.ClientProxy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.internal.LibClient;
import ports.extremereactors.fabric.FabricMod;
import ports.extremereactors.fabric.FabricPlatform;
import ports.extremereactors.platform.ClientPlatform;
import ports.extremereactors.platform.FluidDefinition;
import ports.extremereactors.platform.Network;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Fabric client entrypoint.
 */
public final class FabricClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        final FabricPlatform platform = FabricPlatform.get();

        ClientPlatform.install(new FabricClientPlatform());
        platform.installClientSide(new ClientSide());
        LibClient.initialize();

        ClientTickEvents.START_WORLD_TICK.register(platform::fireLevelTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.trackLevelChange(platform, client.level));
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(FabricPlatform::onBlockEntityUnloaded);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> FabricMod.processApiMessages());

        registerFluidRenderers(platform.getFluids());

        final ClientProxy proxy = new ClientProxy();

        ExtremeReactors.setProxy(proxy);
        proxy.onClientInit();
    }

    /**
     * Fabric has no client level unload event: report the previous level once the client moved to another one or
     * left the world.
     */
    private void trackLevelChange(final FabricPlatform platform, @Nullable final ClientLevel current) {

        if (this._lastLevel != current) {

            if (null != this._lastLevel) {
                platform.fireLevelUnload(this._lastLevel);
            }

            this._lastLevel = current;
        }
    }

    private static void registerFluidRenderers(final Map<Fluid, FluidDefinition> fluids) {

        final Set<ResourceLocation> bucketModels = new HashSet<>();

        fluids.forEach((fluid, definition) -> {

            if (fluid instanceof FlowingFluid flowing && flowing.isSource(flowing.defaultFluidState())) {

                FluidRenderHandlerRegistry.INSTANCE.register(flowing, flowing.getFlowing(),
                        new SimpleFluidRenderHandler(definition.stillTexture(), definition.flowingTexture(),
                                definition.overlayTexture(), definition.tint() & 0xffffff));
                BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), flowing, flowing.getFlowing());

                if (Items.AIR != flowing.getBucket()) {

                    final int tint = definition.tint() & 0xffffff;

                    final ResourceLocation item = Registry.ITEM.getKey(flowing.getBucket());

                    // Forge draws these buckets with its dynamic bucket model; on Fabric, BUCKET_MODEL is the vanilla
                    // bucket with a fluid overlay in layer 1.
                    ColorProviderRegistry.ITEM.register((stack, layer) -> 1 == layer ? tint : -1, flowing.getBucket());
                    bucketModels.add(new ResourceLocation(item.getNamespace(), "item/" + item.getPath()));
                }
            }
        });

        // The shared bucket item models use Forge's "forge:bucket" loader. Fabric hands out BUCKET_MODEL before their
        // JSON is read: Porting Lib (in Tekxit 4) reads the "loader" key, has no forge:bucket loader and fails the
        // model, and a forge:item/bucket_drip stand-in would clash with other ports shipping one.
        ModelLoadingRegistry.INSTANCE.registerResourceProvider(resources -> (id, context) -> {
            if (!bucketModels.contains(id)) {
                return null;
            }
            final BlockModel model = BlockModel.fromString(BUCKET_MODEL);
            model.name = id.toString();
            return model;
        });
    }

    static final String BUCKET_MODEL = "{\"parent\": \"minecraft:item/generated\", \"textures\": "
            + "{\"layer0\": \"minecraft:item/bucket\", \"layer1\": \"bigreactors:item/bucket_fluid_overlay\"}}";

    private static final class ClientSide implements FabricPlatform.ClientSide {

        @Override
        public void registerReceiver(final ResourceLocation channel, final Network.ClientReceiver receiver) {
            ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buffer, sender) -> {

                final FriendlyByteBuf data = new FriendlyByteBuf(buffer.copy());

                client.execute(() -> receiver.receive(data));
            });
        }

        @Override
        public void sendToServer(final ResourceLocation channel, final FriendlyByteBuf data) {
            ClientPlayNetworking.send(channel, data);
        }

        @Override
        public Optional<Level> level() {
            return Optional.ofNullable(Minecraft.getInstance().level);
        }

        @Override
        public Optional<Player> player() {
            return Optional.ofNullable(Minecraft.getInstance().player);
        }

        @Nullable
        @Override
        public RecipeManager recipeManager() {

            final ClientPacketListener connection = Minecraft.getInstance().getConnection();

            return null != connection ? connection.getRecipeManager() : null;
        }
    }

    @Nullable
    private ClientLevel _lastLevel;
}
