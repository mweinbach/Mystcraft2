package net.mystcraft.minecraft.client;

import net.mystcraft.minecraft.client.PageCollectionScreen;

import net.minecraft.client.gui.screens.MenuScreens;
import net.mystcraft.integration.registry.BinderRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.client.render.BookReceptacleRenderer;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.BookstandRegistry;
import net.mystcraft.integration.registry.BookEntityRegistry;
import net.mystcraft.minecraft.client.render.LinkBookRenderer;
import net.mystcraft.minecraft.client.render.BookstandRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge discovers this subscriber only on the physical client. */
@Mod.EventBusSubscriber(modid = "mystcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MystcraftClientEvents {
    private MystcraftClientEvents() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.mystcraft.minecraft.guide.GuideBookItem.installClientOpener(GuideBookClient::open);
            net.mystcraft.minecraft.presentation.BookPresentationOptions.loadClient(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
        net.mystcraft.minecraft.writing.DynamicSymbolPages.setClientRegistries(() -> net.minecraft.client.Minecraft.getInstance().level == null ? null : net.minecraft.client.Minecraft.getInstance().level.registryAccess());
            net.mystcraft.minecraft.entity.MeteorEntity.installClientSound(meteor -> net.minecraft.client.Minecraft.getInstance().getSoundManager().play(new MeteorMovingSound(meteor)));
            net.minecraft.client.renderer.item.ItemProperties.register(net.mystcraft.integration.registry.AgeRegistry.PAGE.get(),
                    new net.minecraft.resources.ResourceLocation("mystcraft", "page"),
                    (stack, level, entity, seed) -> PageItemModels.value(stack));
            MenuScreens.register(net.mystcraft.integration.registry.BookDisplayRegistry.DISPLAY_MENU.get(), BookDisplayScreen::new);
            MenuScreens.register(net.mystcraft.integration.registry.ExplorationRegistry.ARCHIVIST_MENU.get(), ArchivistScreen::new);
            MenuScreens.register(InkMixerRegistry.INK_MIXER_MENU.get(), InkMixerScreen::new);
            MenuScreens.register(BinderRegistry.BOOK_BINDER_MENU.get(), BookBinderScreen::new);
            MenuScreens.register(net.mystcraft.integration.registry.ModifierRegistry.LINK_MODIFIER_MENU.get(), net.mystcraft.minecraft.client.LinkModifierScreen::new);
            MenuScreens.register(WritingDeskRegistry.WRITING_DESK_MENU.get(), WritingDeskScreen::new);
            MenuScreens.register(WritingDeskRegistry.PAGE_COLLECTION_MENU.get(), PageCollectionScreen::new);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(PortalRegistry.CRYSTAL.get(), net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(PortalRegistry.LINK_PORTAL.get(), net.minecraft.client.renderer.RenderType.translucent());
        });
    }
    @SubscribeEvent
    public static void registerColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, view, pos, tint) -> ClassicPortalColor.color(view, pos), PortalRegistry.LINK_PORTAL.get());
    }
    @SubscribeEvent
    public static void registerParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.register(net.mystcraft.integration.registry.LinkEffectsRegistry.LINK.get(), ClassicLinkParticle.Provider::new);
    }
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PortalRegistry.RECEPTACLE_ENTITY.get(), BookReceptacleRenderer::new);
        event.registerBlockEntityRenderer(net.mystcraft.integration.registry.StarFissureRegistry.STAR_FISSURE_ENTITY.get(), net.mystcraft.minecraft.client.render.StarFissureRenderer::new);
        event.registerBlockEntityRenderer(net.mystcraft.integration.registry.ExplorationRegistry.LECTERN_ENTITY.get(), net.mystcraft.minecraft.client.render.MystLecternRenderer::new);
        event.registerEntityRenderer(BookEntityRegistry.LINK_BOOK.get(), LinkBookRenderer::new);
        event.registerEntityRenderer(net.mystcraft.integration.registry.MeteorRegistry.METEOR.get(), net.mystcraft.minecraft.client.render.MeteorRenderer::new);
        event.registerEntityRenderer(net.mystcraft.integration.registry.DecayFallingRegistry.FALLING.get(), net.mystcraft.minecraft.client.render.DecayFallingRenderer::new);
        event.registerEntityRenderer(net.mystcraft.integration.registry.LightningRegistry.LIGHTNING.get(), net.mystcraft.minecraft.client.age.ClassicLightningRenderer::new);
        event.registerBlockEntityRenderer(BookstandRegistry.BOOKSTAND_ENTITY.get(), BookstandRenderer::new);
    }
}
