package ports.jurassicraft.darts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DartRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, PortMod.MOD_ID);
    public static final RegistryObject<Item> TRANQUILIZER = dart("dart_tranquilizer", DartItem.Kind.TRANQUILIZER);
    public static final RegistryObject<Item> POISON = dart("dart_poison_cycasin", DartItem.Kind.POISON);
    public static final RegistryObject<Item> LETHAL = dart("dart_poison_executioner_concoction", DartItem.Kind.LETHAL);
    public static final RegistryObject<Item> POTION = dart("dart_tipped_potion", DartItem.Kind.POTION);
    public static final RegistryObject<RecipeSerializer<PotionDartRecipe>> POTION_RECIPE = RECIPES.register("dart_tipped_potion",
            () -> new SimpleRecipeSerializer<>(PotionDartRecipe::new));
    public static final RegistryObject<Item> GUN = ITEMS.register("dart_gun", () -> new DartGunItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final RegistryObject<EntityType<DartProjectile>> PROJECTILE = ENTITIES.register("dart", () -> EntityType.Builder
            .<DartProjectile>of(DartProjectile::new, MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(4).updateInterval(10).build("jurassicraft:dart"));
    private DartRegistry() {}
    private static RegistryObject<Item> dart(String id, DartItem.Kind kind) {
        return ITEMS.register(id, () -> new DartItem(kind, new Item.Properties().tab(JurassicContent.TAB)));
    }
    public static void register(IEventBus bus) { ITEMS.register(bus); ENTITIES.register(bus); RECIPES.register(bus); }
}
