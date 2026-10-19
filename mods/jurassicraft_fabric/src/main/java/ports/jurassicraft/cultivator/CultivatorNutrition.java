package ports.jurassicraft.cultivator;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.supplies.DinosaurSupplies;

/** The original food-to-nutrient conversion coefficients. */
public final class CultivatorNutrition {
    public record Nutrients(double proximates, double minerals, double vitamins, double lipids) {}
    private static final Map<Item, Nutrients> FOODS = new IdentityHashMap<>();
    static {
        add(Items.APPLE,.060,.065,.100,.010); add(Items.POTATO,.100,.200,.160,.020);
        add(Items.BREAD,.300,.400,.430,.180); add(Items.CHICKEN,.390,.350,.280,.450);
        add(Items.COOKED_CHICKEN,.490,.425,.335,.555); add(Items.PORKCHOP,.460,.310,.390,.380);
        add(Items.COOKED_PORKCHOP,.580,.390,.490,.470); add(Items.BEEF,.460,.310,.390,.380);
        add(Items.COOKED_BEEF,.520,.330,.410,.400);
        for (Item fish : new Item[]{Items.COD,Items.SALMON,Items.TROPICAL_FISH,Items.PUFFERFISH}) add(fish,.480,.430,.140,.240);
        for (Item fish : new Item[]{Items.COOKED_COD,Items.COOKED_SALMON}) add(fish,.500,.450,.200,.280);
        add(Items.MILK_BUCKET,.180,.260,.220,.600); add(Items.EGG,.050,.030,.050,.250);
        add(Items.CARROT,.070,.170,.350,.010); add(Items.SUGAR,.200,.010,.010,.010);
        add(Items.MELON_SLICE,.060,.060,.060,.010); add(Items.WHEAT,.100,.220,.100,.030);
        add(Items.MUTTON,.460,.310,.390,.380); add(Items.COOKED_MUTTON,.580,.390,.490,.470);
    }
    private CultivatorNutrition() {}
    private static void add(Item item, double p, double m, double v, double l) { FOODS.put(item,new Nutrients(p,m,v,l)); }
    private static final class DinosaurFoods {
        private static final Map<Item,Nutrients> FOODS = foods();
        private static Map<Item,Nutrients> foods() {
            Map<Item,Nutrients> values=new IdentityHashMap<>();
            for (Species species : Species.values()) {
                values.put(DinosaurRegistry.rawMeat(species).get(),new Nutrients(.460,.310,.390,.380));
                values.put(DinosaurRegistry.cookedMeat(species).get(),new Nutrients(.580,.390,.490,.470));
            }
            return values;
        }
    }
    public static Nutrients get(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(DinosaurRegistry.DINOSAUR_MEAT.get())) return new Nutrients(.460,.310,.390,.380);
        if (stack.is(DinosaurSupplies.STEAK.get())) return new Nutrients(.580,.390,.490,.470);
        Nutrients vanilla=FOODS.get(stack.getItem());
        return vanilla!=null ? vanilla : DinosaurFoods.FOODS.get(stack.getItem());
    }
}
