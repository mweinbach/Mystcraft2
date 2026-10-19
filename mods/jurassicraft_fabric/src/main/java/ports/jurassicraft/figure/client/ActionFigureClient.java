package ports.jurassicraft.figure.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.figure.ActionFigureCycleRecipe;

public final class ActionFigureClient {
    private ActionFigureClient() {}
    public static void init() {
        BlockEntityRenderers.register(ActionFigureRegistry.ENTITY.get(), context -> new ActionFigureRenderer());
        ItemProperties.register(ActionFigureRegistry.ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "figure_species"),
                (stack, level, entity, seed) -> ActionFigureCycleRecipe.ORDER.indexOf(ActionFigureItem.species(stack)));
    }
}
