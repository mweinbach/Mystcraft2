package ports.jurassicraft.figure;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class ActionFigureRegistry {
    public static final Supplier<Block> BLOCK = Platform.register(Registry.BLOCK_REGISTRY, "action_figure", ActionFigureBlock::new);
    public static final Supplier<Item> ITEM = Platform.register(Registry.ITEM_REGISTRY, "action_figure", () -> new ActionFigureItem(BLOCK.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<BlockEntityType<ActionFigureBlockEntity>> ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "action_figure",
            () -> BlockEntityType.Builder.of(ActionFigureBlockEntity::new, BLOCK.get()).build(null));
    public static final Supplier<RecipeSerializer<ActionFigureCycleRecipe>> CYCLE = Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY, "action_figure_cycle",
            () -> new SimpleRecipeSerializer<>(ActionFigureCycleRecipe::new));
    private ActionFigureRegistry() {}
    public static void init() { }
}
