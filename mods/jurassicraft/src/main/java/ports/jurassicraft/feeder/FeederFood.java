package ports.jurassicraft.feeder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.PortMod;

/** Tags allow modpacks to customize what the feeder accepts. */
public final class FeederFood {
    public static final TagKey<Item> MEAT = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "feeder_meat"));
    public static final TagKey<Item> PLANTS = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "feeder_plants"));

    private FeederFood() {}

    public static boolean meat(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(MEAT)) return true;
        if (stack.is(PLANTS)) return false;
        FoodProperties food = stack.getFoodProperties(null);
        return food != null && food.isMeat();
    }

    public static boolean plant(ItemStack stack) {
        if (stack.isEmpty() || stack.is(MEAT)) return false;
        if (stack.is(PLANTS)) return true;
        FoodProperties food = stack.getFoodProperties(null);
        return food != null && !food.isMeat();
    }
}
