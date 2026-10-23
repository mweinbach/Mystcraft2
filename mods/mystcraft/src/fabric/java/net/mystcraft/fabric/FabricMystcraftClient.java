package net.mystcraft.fabric;

import java.util.UUID;
import net.mystcraft.integration.registry.BinderRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.client.BookBinderScreen;
import net.mystcraft.minecraft.client.WritingDeskScreen;
import net.mystcraft.minecraft.client.PageCollectionScreen;
import net.mystcraft.minecraft.client.render.BookReceptacleRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.FriendlyByteBuf;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.integration.registry.BookEntityRegistry;
import net.mystcraft.integration.registry.BookstandRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.minecraft.client.InkMixerScreen;
import net.mystcraft.minecraft.client.LinkingBookClient;
import net.mystcraft.minecraft.client.render.BookstandRenderer;
import net.mystcraft.minecraft.client.render.LinkBookRenderer;

public final class FabricMystcraftClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.mystcraft.fabric.fluid.FabricInkClient.initialize();
        net.mystcraft.minecraft.presentation.BookPresentationOptions.loadClient(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(FabricLinkingTransport.PRESENTATION_OPTIONS,
                (client, handler, data, response) -> {
                    boolean labels = data.readBoolean();
                    client.execute(() -> net.mystcraft.minecraft.presentation.BookPresentationOptions.acceptServer(labels));
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                net.mystcraft.minecraft.presentation.BookPresentationOptions.acceptServer(false));
        net.mystcraft.minecraft.writing.DynamicSymbolPages.setClientRegistries(() -> net.minecraft.client.Minecraft.getInstance().level == null ? null : net.minecraft.client.Minecraft.getInstance().level.registryAccess());
        net.mystcraft.minecraft.entity.MeteorEntity.installClientSound(meteor -> net.minecraft.client.Minecraft.getInstance().getSoundManager().play(new net.mystcraft.minecraft.client.MeteorMovingSound(meteor)));
        FabricAgeVisualClient.initialize();
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                net.mystcraft.integration.registry.ExplorationRegistry.LECTERN_ITEM.get(),
                (stack, transform, pose, buffers, light, overlay) -> net.mystcraft.minecraft.client.model.ClassicLecternItemModel.render(pose, buffers, light, overlay));
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register((state, view, pos, tint) -> net.mystcraft.minecraft.client.ClassicPortalColor.color(view, pos), PortalRegistry.LINK_PORTAL.get());
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(net.mystcraft.integration.registry.LinkEffectsRegistry.LINK.get(), net.mystcraft.minecraft.client.ClassicLinkParticle.Provider::new);
        ClientPlayNetworking.registerGlobalReceiver(FabricLinkingTransport.OPEN_BOOK,
                (client, handler, data, response) -> {
                    LinkingNetwork.OpenBook packet = LinkingNetwork.OpenBook.decode(data);
                    client.execute(() -> LinkingBookClient.open(packet));
                });
        ItemProperties.register(
                net.mystcraft.integration.registry.AgeRegistry.PAGE.get(), new net.minecraft.resources.ResourceLocation("mystcraft", "page"),
                (stack, level, entity, seed) -> net.mystcraft.minecraft.client.PageItemModels.value(stack));
        MenuScreens.register(BinderRegistry.BOOK_BINDER_MENU.get(), BookBinderScreen::new);
        MenuScreens.register(net.mystcraft.integration.registry.ModifierRegistry.LINK_MODIFIER_MENU.get(), net.mystcraft.minecraft.client.LinkModifierScreen::new);
        MenuScreens.register(WritingDeskRegistry.WRITING_DESK_MENU.get(), WritingDeskScreen::new);
        MenuScreens.register(WritingDeskRegistry.PAGE_COLLECTION_MENU.get(), PageCollectionScreen::new);
        BlockEntityRenderers.register(PortalRegistry.RECEPTACLE_ENTITY.get(), BookReceptacleRenderer::new);
        BlockEntityRenderers.register(net.mystcraft.integration.registry.StarFissureRegistry.STAR_FISSURE_ENTITY.get(), net.mystcraft.minecraft.client.render.StarFissureRenderer::new);
        BlockEntityRenderers.register(net.mystcraft.integration.registry.ExplorationRegistry.LECTERN_ENTITY.get(), net.mystcraft.minecraft.client.render.MystLecternRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.renderer.RenderType.translucent(), PortalRegistry.CRYSTAL.get(), PortalRegistry.LINK_PORTAL.get());
        MenuScreens.register(net.mystcraft.integration.registry.BookDisplayRegistry.DISPLAY_MENU.get(), net.mystcraft.minecraft.client.BookDisplayScreen::new);
        MenuScreens.register(net.mystcraft.integration.registry.ExplorationRegistry.ARCHIVIST_MENU.get(), net.mystcraft.minecraft.client.ArchivistScreen::new);
        MenuScreens.register(InkMixerRegistry.INK_MIXER_MENU.get(), InkMixerScreen::new);
        EntityRendererRegistry.register(BookEntityRegistry.LINK_BOOK.get(), LinkBookRenderer::new);
        EntityRendererRegistry.register(net.mystcraft.integration.registry.MeteorRegistry.METEOR.get(), net.mystcraft.minecraft.client.render.MeteorRenderer::new);
        EntityRendererRegistry.register(net.mystcraft.integration.registry.DecayFallingRegistry.FALLING.get(), net.mystcraft.minecraft.client.render.DecayFallingRenderer::new);
        EntityRendererRegistry.register(net.mystcraft.integration.registry.LightningRegistry.LIGHTNING.get(), net.mystcraft.minecraft.client.age.ClassicLightningRenderer::new);
        BlockEntityRenderers.register(BookstandRegistry.BOOKSTAND_ENTITY.get(), BookstandRenderer::new);
    }
    public static void sendRequest(UUID token) {
        FriendlyByteBuf data = PacketByteBufs.create();
        data.writeUUID(token);
        ClientPlayNetworking.send(FabricLinkingTransport.LINK, data);
    }
}
