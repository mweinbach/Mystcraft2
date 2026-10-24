package ports.buildcraft.forge;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.buildcraft.builders.SnapshotTooltip;
import ports.buildcraft.client.BCClient;
import ports.buildcraft.client.ClientPlatform;

/** Forge client hooks: renderers through the registration event, sprites through texture stitching. */
final class ForgeClient implements ClientPlatform.Backend {
    private ForgeClient() {}

    static void init(IEventBus modBus) {
        ClientPlatform.install(new ForgeClient());
        Map<ResourceLocation, Consumer<FriendlyByteBuf>> receivers = BCClient.receivers();
        ForgeNetwork.setClientReceiver((channel, payload) -> {
            Consumer<FriendlyByteBuf> receiver = receivers.get(channel);
            if (receiver != null) receiver.accept(payload);
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) BCClient.renderLevel(event.getPoseStack(), event.getCamera());
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            BCClient.registerRenderers(event::registerBlockEntityRenderer);
            BCClient.registerEntityRenderers(event::registerEntityRenderer);
        });
        modBus.addListener((TextureStitchEvent.Pre event) -> {
            if (event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) BCClient.extraSprites().forEach(event::addSprite);
        });
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> BCClient.registerItemColours(event::register));
        modBus.addListener((RegisterClientTooltipComponentFactoriesEvent event) -> event.register(SnapshotTooltip.class, BCClient::tooltipComponent));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            BCClient.registerScreens();
            BCClient.registerItemProperties(ItemProperties::register);
        }));
    }

    @Override
    public TextureAtlasSprite fluidSprite(Fluid fluid) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(IClientFluidTypeExtensions.of(fluid).getStillTexture());
    }

    @Override
    public int fluidColour(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    @Override
    public void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> payload) {
        ForgeNetwork.sendToServer(channel, payload);
    }
}
