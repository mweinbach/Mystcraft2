package ports.jurassicraft.decor;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DecorRegistry {
    private static Block clearGlass, clearPane, reinforcedGlass, reinforcedPane;
    private static EntityType<ParkSignEntity> attractionEntity, paddockEntity;
    private static Item attraction, paddock;
    private static RecipeSerializer<AttractionSignCycleRecipe> cycle;
    public static final Supplier<Block> CLEAR_GLASS = () -> clearGlass;
    public static final Supplier<Block> CLEAR_GLASS_PANE = () -> clearPane;
    public static final Supplier<Block> REINFORCED_GLASS = () -> reinforcedGlass;
    public static final Supplier<Block> REINFORCED_GLASS_PANE = () -> reinforcedPane;
    public static final Supplier<EntityType<ParkSignEntity>> ATTRACTION_SIGN_ENTITY = () -> attractionEntity;
    public static final Supplier<EntityType<ParkSignEntity>> PADDOCK_SIGN_ENTITY = () -> paddockEntity;
    public static final Supplier<Item> ATTRACTION_SIGN = () -> attraction;
    public static final Supplier<Item> PADDOCK_SIGN = () -> paddock;
    public static final Supplier<RecipeSerializer<AttractionSignCycleRecipe>> SIGN_CYCLE = () -> cycle;
    private DecorRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static BlockBehaviour.Properties glass(float hardness, float resistance, SoundType sound) {
        return BlockBehaviour.Properties.of(Material.GLASS).strength(hardness, resistance).sound(sound).noOcclusion();
    }
    private static Block block(String path, Block block) {
        Registry.register(Registry.BLOCK, id(path), block);
        Registry.register(Registry.ITEM, id(path), new BlockItem(block, new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
    private static EntityType<ParkSignEntity> sign(String path, float width, float height, int range) {
        return Registry.register(Registry.ENTITY_TYPE, id(path), EntityType.Builder.<ParkSignEntity>of(ParkSignEntity::new, MobCategory.MISC)
                .sized(width, height).clientTrackingRange(range).updateInterval(10).build(PortMod.MOD_ID + ":" + path));
    }
    public static void register() {
        clearGlass = block("clear_glass", new ConnectedGlassBlock(glass(.3F, .3F, SoundType.GLASS)));
        clearPane = block("clear_glass_pane", new IronBarsBlock(glass(.3F, .3F, SoundType.GLASS)));
        reinforcedGlass = block("reinforced_glass", new ConnectedGlassBlock(glass(10, 14, SoundType.STONE)));
        reinforcedPane = block("reinforced_glass_pane", new IronBarsBlock(glass(10, 14, SoundType.STONE)));
        attractionEntity = sign("attraction_sign", 8, 8, 12); paddockEntity = sign("paddock_sign", 1, 1, 10);
        attraction = Registry.register(Registry.ITEM, id("attraction_sign"), new ParkSignItem(true));
        paddock = Registry.register(Registry.ITEM, id("paddock_sign"), new ParkSignItem(false));
        cycle = Registry.register(Registry.RECIPE_SERIALIZER, id("attraction_sign_cycle"), new SimpleRecipeSerializer<>(AttractionSignCycleRecipe::new));
        ParkSignNetwork.register();
    }
}
