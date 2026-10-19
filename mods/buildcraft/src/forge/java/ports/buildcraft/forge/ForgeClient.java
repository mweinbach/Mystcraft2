package ports.buildcraft.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.buildcraft.client.BCClient;
import ports.buildcraft.client.ClientPlatform;

/** Forge client hooks: renderers through the registration event, sprites through texture stitching. */
final class ForgeClient implements ClientPlatform.Backend {
    private ForgeClient() {}

    static void init(IEventBus modBus) {
        ClientPlatform.install(new ForgeClient());
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> BCClient.registerRenderers(event::registerBlockEntityRenderer));
        modBus.addListener((TextureStitchEvent.Pre event) -> {
            if (event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) BCClient.extraSprites().forEach(event::addSprite);
        });
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ForgeClient::setup));
    }

    @SuppressWarnings("deprecation")
    private static void setup() {
        BCClient.registerScreens();
        for (Block block : BCClient.cutoutBlocks()) ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
    }

    @Override
    public TextureAtlasSprite fluidSprite(Fluid fluid) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(IClientFluidTypeExtensions.of(fluid).getStillTexture());
    }

    @Override
    public int fluidColour(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor();
    }
}
