package ports.jurassicraft.decor;

import java.util.function.Supplier;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DecorRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, PortMod.MOD_ID);
    public static final RegistryObject<Block> CLEAR_GLASS = block("clear_glass", () -> new ConnectedGlassBlock(glass(0.3F, 0.3F, SoundType.GLASS)));
    public static final RegistryObject<Block> CLEAR_GLASS_PANE = block("clear_glass_pane", () -> new IronBarsBlock(glass(0.3F, 0.3F, SoundType.GLASS)));
    public static final RegistryObject<Block> REINFORCED_GLASS = block("reinforced_glass", () -> new ConnectedGlassBlock(glass(10.0F, 14.0F, SoundType.STONE)));
    public static final RegistryObject<Block> REINFORCED_GLASS_PANE = block("reinforced_glass_pane", () -> new IronBarsBlock(glass(10.0F, 14.0F, SoundType.STONE)));
    public static final RegistryObject<EntityType<ParkSignEntity>> ATTRACTION_SIGN_ENTITY = ENTITIES.register("attraction_sign",
            () -> EntityType.Builder.<ParkSignEntity>of(ParkSignEntity::new, MobCategory.MISC).sized(8.0F, 8.0F)
                    .clientTrackingRange(12).updateInterval(10).build(PortMod.MOD_ID + ":attraction_sign"));
    public static final RegistryObject<EntityType<ParkSignEntity>> PADDOCK_SIGN_ENTITY = ENTITIES.register("paddock_sign",
            () -> EntityType.Builder.<ParkSignEntity>of(ParkSignEntity::new, MobCategory.MISC).sized(1.0F, 1.0F)
                    .clientTrackingRange(10).updateInterval(10).build(PortMod.MOD_ID + ":paddock_sign"));
    public static final RegistryObject<Item> ATTRACTION_SIGN = ITEMS.register("attraction_sign", () -> new ParkSignItem(true));
    public static final RegistryObject<Item> PADDOCK_SIGN = ITEMS.register("paddock_sign", () -> new ParkSignItem(false));
    public static final RegistryObject<RecipeSerializer<AttractionSignCycleRecipe>> SIGN_CYCLE = RECIPES.register("attraction_sign_cycle",
            () -> new SimpleRecipeSerializer<>(AttractionSignCycleRecipe::new));

    private DecorRegistry() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); RECIPES.register(bus);
        ParkSignNetwork.register();
    }

    private static BlockBehaviour.Properties glass(float hardness, float resistance, SoundType sound) {
        return BlockBehaviour.Properties.of(Material.GLASS).strength(hardness, resistance).sound(sound).noOcclusion();
    }
    private static RegistryObject<Block> block(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
}
