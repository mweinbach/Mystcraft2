package ports.buildcraft.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.client.BCClient;
import ports.buildcraft.client.ClientPlatform;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.BCFluidDefinition;

/** Fabric client hooks: render layers, fluid render handlers, extra atlas sprites, renderers and screens. */
public final class FabricClientMod implements ClientModInitializer, ClientPlatform.Backend {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(this);
        BCClient.registerRenderers(BlockEntityRenderers::register);
        BCClient.registerScreens();
        BCClient.registerItemColours(ColorProviderRegistry.ITEM::register);
        BCClient.registerItemProperties(FabricModelPredicateProviderRegistry::register);
        for (Block block : BCClient.cutoutBlocks()) BlockRenderLayerMap.INSTANCE.putBlock(block, RenderType.cutout());
        for (BCFluidDefinition fluid : BCEnergy.FLUIDS) {
            FluidRenderHandlerRegistry.INSTANCE.register(fluid.source(), fluid.flowing(),
                    new SimpleFluidRenderHandler(fluid.stillTexture(), fluid.flowingTexture()));
        }
        ClientSpriteRegistryCallback.event(InventoryMenu.BLOCK_ATLAS).register((atlas, registry) -> BCClient.extraSprites().forEach(registry::register));
        BCClient.receivers().forEach((channel, receiver) -> ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buffer, sender) -> {
            FriendlyByteBuf payload = new FriendlyByteBuf(buffer.copy());
            client.execute(() -> {
                try {
                    receiver.accept(payload);
                } finally {
                    payload.release();
                }
            });
        }));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> BCClient.renderLevel(context.matrixStack(), context.camera()));
    }

    @Override
    public TextureAtlasSprite fluidSprite(Fluid fluid) {
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(FluidVariant.of(fluid));
        return sprite != null ? sprite : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
    }

    @Override
    public int fluidColour(Fluid fluid) {
        return FluidVariantRendering.getColor(FluidVariant.of(fluid)) | 0xFF000000;
    }
}
