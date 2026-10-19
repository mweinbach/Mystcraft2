package ports.jurassicraft.decor;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
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
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DecorRegistry {
    public static final Supplier<Block> CLEAR_GLASS = block("clear_glass", () -> new ConnectedGlassBlock(glass(0.3F, 0.3F, SoundType.GLASS)));
    public static final Supplier<Block> CLEAR_GLASS_PANE = block("clear_glass_pane", () -> new IronBarsBlock(glass(0.3F, 0.3F, SoundType.GLASS)));
    public static final Supplier<Block> REINFORCED_GLASS = block("reinforced_glass", () -> new ConnectedGlassBlock(glass(10.0F, 14.0F, SoundType.STONE)));
    public static final Supplier<Block> REINFORCED_GLASS_PANE = block("reinforced_glass_pane", () -> new IronBarsBlock(glass(10.0F, 14.0F, SoundType.STONE)));
    public static final Supplier<EntityType<ParkSignEntity>> ATTRACTION_SIGN_ENTITY = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "attraction_sign",
            () -> EntityType.Builder.<ParkSignEntity>of(ParkSignEntity::new, MobCategory.MISC).sized(8.0F, 8.0F)
                    .clientTrackingRange(12).updateInterval(10).build(PortMod.MOD_ID + ":attraction_sign"));
    public static final Supplier<EntityType<ParkSignEntity>> PADDOCK_SIGN_ENTITY = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "paddock_sign",
            () -> EntityType.Builder.<ParkSignEntity>of(ParkSignEntity::new, MobCategory.MISC).sized(1.0F, 1.0F)
                    .clientTrackingRange(10).updateInterval(10).build(PortMod.MOD_ID + ":paddock_sign"));
    public static final Supplier<Item> ATTRACTION_SIGN = Platform.register(Registry.ITEM_REGISTRY, "attraction_sign", () -> new ParkSignItem(true));
    public static final Supplier<Item> PADDOCK_SIGN = Platform.register(Registry.ITEM_REGISTRY, "paddock_sign", () -> new ParkSignItem(false));
    public static final Supplier<RecipeSerializer<AttractionSignCycleRecipe>> SIGN_CYCLE = Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY, "attraction_sign_cycle",
            () -> new SimpleRecipeSerializer<>(AttractionSignCycleRecipe::new));

    private DecorRegistry() {}
    public static void init() {
        ParkSignNetwork.register();
    }

    private static BlockBehaviour.Properties glass(float hardness, float resistance, SoundType sound) {
        return BlockBehaviour.Properties.of(Material.GLASS).strength(hardness, resistance).sound(sound).noOcclusion();
    }
    private static Supplier<Block> block(String id, Supplier<Block> factory) {
        Supplier<Block> block = Platform.register(Registry.BLOCK_REGISTRY, id, factory);
        Platform.register(Registry.ITEM_REGISTRY, id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
}
