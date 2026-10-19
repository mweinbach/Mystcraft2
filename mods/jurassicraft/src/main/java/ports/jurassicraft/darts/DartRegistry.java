package ports.jurassicraft.darts;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DartRegistry {
    public static final Supplier<Item> TRANQUILIZER = dart("dart_tranquilizer", DartItem.Kind.TRANQUILIZER);
    public static final Supplier<Item> POISON = dart("dart_poison_cycasin", DartItem.Kind.POISON);
    public static final Supplier<Item> LETHAL = dart("dart_poison_executioner_concoction", DartItem.Kind.LETHAL);
    public static final Supplier<Item> POTION = dart("dart_tipped_potion", DartItem.Kind.POTION);
    public static final Supplier<RecipeSerializer<PotionDartRecipe>> POTION_RECIPE = Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY, "dart_tipped_potion",
            () -> new SimpleRecipeSerializer<>(PotionDartRecipe::new));
    public static final Supplier<Item> GUN = Platform.register(Registry.ITEM_REGISTRY, "dart_gun", () -> new DartGunItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final Supplier<EntityType<DartProjectile>> PROJECTILE = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "dart", () -> EntityType.Builder
            .<DartProjectile>of(DartProjectile::new, MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(4).updateInterval(10).build("jurassicraft:dart"));
    private DartRegistry() {}
    private static Supplier<Item> dart(String id, DartItem.Kind kind) {
        return Platform.register(Registry.ITEM_REGISTRY, id, () -> new DartItem(kind, new Item.Properties().tab(JurassicContent.TAB)));
    }
    public static void init() { }
}
