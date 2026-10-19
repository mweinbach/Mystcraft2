package ports.jurassicraft.darts;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DartRegistry {
    public static final class Entry<T> implements Supplier<T> {
        private T value;
        private void set(T value) { this.value = Objects.requireNonNull(value); }
        @Override public T get() { return Objects.requireNonNull(value, "Dart registry not initialized"); }
    }
    public static final Entry<Item> TRANQUILIZER = new Entry<>(), POISON = new Entry<>(), LETHAL = new Entry<>(),
            POTION = new Entry<>(), GUN = new Entry<>();
    public static final Entry<EntityType<DartProjectile>> PROJECTILE = new Entry<>();
    public static final Entry<RecipeSerializer<PotionDartRecipe>> POTION_RECIPE = new Entry<>();
    private DartRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static void dart(Entry<Item> entry, String name, DartItem.Kind kind) {
        entry.set(Registry.register(Registry.ITEM, id(name), new DartItem(kind, new Item.Properties().tab(JurassicContent.TAB))));
    }
    public static void init() {
        dart(TRANQUILIZER, "dart_tranquilizer", DartItem.Kind.TRANQUILIZER);
        dart(POISON, "dart_poison_cycasin", DartItem.Kind.POISON);
        dart(LETHAL, "dart_poison_executioner_concoction", DartItem.Kind.LETHAL);
        dart(POTION, "dart_tipped_potion", DartItem.Kind.POTION);
        GUN.set(Registry.register(Registry.ITEM, id("dart_gun"),
                new DartGunItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1))));
        POTION_RECIPE.set(Registry.register(Registry.RECIPE_SERIALIZER, id("dart_tipped_potion"),
                new SimpleRecipeSerializer<>(PotionDartRecipe::new)));
        PROJECTILE.set(Registry.register(Registry.ENTITY_TYPE, id("dart"), EntityType.Builder
                .<DartProjectile>of(DartProjectile::new, MobCategory.MISC).sized(.25F, .25F)
                .clientTrackingRange(4).updateInterval(10).build(id("dart").toString())));
    }
}
