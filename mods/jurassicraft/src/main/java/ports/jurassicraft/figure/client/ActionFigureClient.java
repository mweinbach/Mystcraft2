package ports.jurassicraft.figure.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.figure.ActionFigureCycleRecipe;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class ActionFigureClient {
    private ActionFigureClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(ActionFigureRegistry.ENTITY.get(), context -> new ActionFigureRenderer());
    }
    public static void setup() {
        ItemProperties.register(ActionFigureRegistry.ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "figure_species"),
                (stack, level, entity, seed) -> ActionFigureCycleRecipe.ORDER.indexOf(ActionFigureItem.species(stack)));
    }
}
