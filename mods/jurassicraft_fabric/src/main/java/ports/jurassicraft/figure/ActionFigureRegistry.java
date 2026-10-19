package ports.jurassicraft.figure;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class ActionFigureRegistry {
    private static Block block;
    private static Item item;
    private static BlockEntityType<ActionFigureBlockEntity> entity;
    private static RecipeSerializer<ActionFigureCycleRecipe> cycle;
    public static final Supplier<Block> BLOCK = () -> block;
    public static final Supplier<Item> ITEM = () -> item;
    public static final Supplier<BlockEntityType<ActionFigureBlockEntity>> ENTITY = () -> entity;
    public static final Supplier<RecipeSerializer<ActionFigureCycleRecipe>> CYCLE = () -> cycle;
    private ActionFigureRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    public static void register() {
        block = Registry.register(Registry.BLOCK, id("action_figure"), new ActionFigureBlock());
        item = Registry.register(Registry.ITEM, id("action_figure"), new ActionFigureItem(block, new Item.Properties().tab(JurassicContent.TAB)));
        entity = Registry.register(Registry.BLOCK_ENTITY_TYPE, id("action_figure"), BlockEntityType.Builder.of(ActionFigureBlockEntity::new, block).build(null));
        cycle = Registry.register(Registry.RECIPE_SERIALIZER, id("action_figure_cycle"), new SimpleRecipeSerializer<>(ActionFigureCycleRecipe::new));
    }
}
