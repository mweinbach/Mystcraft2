package ports.jurassicraft.figure;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class ActionFigureRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, PortMod.MOD_ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("action_figure", ActionFigureBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("action_figure", () -> new ActionFigureItem(BLOCK.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<BlockEntityType<ActionFigureBlockEntity>> ENTITY = ENTITIES.register("action_figure",
            () -> BlockEntityType.Builder.of(ActionFigureBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<RecipeSerializer<ActionFigureCycleRecipe>> CYCLE = RECIPES.register("action_figure_cycle",
            () -> new SimpleRecipeSerializer<>(ActionFigureCycleRecipe::new));
    private ActionFigureRegistry() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); RECIPES.register(bus); }
}
