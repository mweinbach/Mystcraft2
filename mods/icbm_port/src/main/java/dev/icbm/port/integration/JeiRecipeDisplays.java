package dev.icbm.port.integration;

import dev.icbm.port.delivery.*;
import dev.icbm.port.payload.PayloadType;
import java.util.*;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;

/** Client recipe-viewer examples use the same cargo/manifest encoding as server serializers. */
public final class JeiRecipeDisplays {
  private JeiRecipeDisplays() {}

  public static String subtype(ItemStack stack) {
    if (stack.isOf(DeliveryContent.CLUSTER_MISSILE))
      return ClusterPlan.fromStack(stack).payloads().toString();
    if (stack.isOf(DeliveryContent.HELD_ITEM_MISSILE))
      return HeldItemMissileItem.cargo(stack).writeNbt(new net.minecraft.nbt.NbtCompound())
          + "|"
          + HeldItemMissileItem.mode(stack);
    if (CarrierData.isCarrier(stack))
      return CarrierData.cargo(stack).writeNbt(new net.minecraft.nbt.NbtCompound())
          + "|"
          + CarrierData.mode(stack);
    return "";
  }

  public static List<CraftingRecipe> all() {
    List<CraftingRecipe> recipes = new ArrayList<>();
    for (Item item : Registry.ITEM) {
      if (item == Items.AIR) continue;
      recipes.addAll(forCargo(new ItemStack(item)));
    }
    for (var entry : DeliveryContent.BOMBLETS.entrySet()) {
      ItemStack loaded = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
      new ClusterPlan(List.of(entry.getKey())).writeTo(loaded);
      recipes.add(
          display(
              "cluster/" + entry.getKey().id(),
              loaded,
              ItemStack.EMPTY,
              new ItemStack(DeliveryContent.CLUSTER_MISSILE),
              new ItemStack(entry.getValue())));
    }
    if (DeliveryContent.BOMBLETS.containsKey(PayloadType.CONDENSED)
        && DeliveryContent.BOMBLETS.containsKey(PayloadType.INCENDIARY)) {
      ItemStack loaded = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
      new ClusterPlan(List.of(PayloadType.CONDENSED, PayloadType.INCENDIARY)).writeTo(loaded);
      recipes.add(
          display(
              "cluster/mixed",
              loaded,
              ItemStack.EMPTY,
              new ItemStack(DeliveryContent.CLUSTER_MISSILE),
              new ItemStack(DeliveryContent.BOMBLETS.get(PayloadType.CONDENSED)),
              new ItemStack(DeliveryContent.BOMBLETS.get(PayloadType.INCENDIARY))));
    }
    return List.copyOf(recipes);
  }

  public static List<CraftingRecipe> forCargo(ItemStack cargo) {
    List<CraftingRecipe> recipes = new ArrayList<>();
    if (cargo.isEmpty()) return recipes;
    Identifier itemId = Registry.ITEM.getId(cargo.getItem());
    String suffix = itemId.getNamespace() + "/" + itemId.getPath();
    if (HeldItemMissileItem.allowed(cargo)) {
      ItemStack empty = new ItemStack(DeliveryContent.HELD_ITEM_MISSILE), loaded = empty.copy();
      HeldItemMissileItem.setCargo(loaded, cargo);
      ItemStack returned = loaded.copy();
      HeldItemMissileItem.setCargo(returned, ItemStack.EMPTY);
      recipes.add(display("held/load/" + suffix, loaded, ItemStack.EMPTY, empty, cargo));
      recipes.add(display("held/unload/" + suffix, cargo, returned, loaded));
    }
    if (CarrierData.allowed(cargo))
      for (Item carrier : List.of(DeliveryContent.CARGO_PARACHUTE, DeliveryContent.CARGO_BALLOON)) {
        ItemStack empty = new ItemStack(carrier), loaded = empty.copy();
        CarrierData.setCargo(loaded, cargo);
        ItemStack returned = loaded.copy();
        CarrierData.setCargo(returned, ItemStack.EMPTY);
        String type = carrier == DeliveryContent.CARGO_PARACHUTE ? "parachute" : "balloon";
        recipes.add(display(type + "/load/" + suffix, loaded, ItemStack.EMPTY, empty, cargo));
        recipes.add(display(type + "/unload/" + suffix, cargo, returned, loaded));
      }
    return List.copyOf(recipes);
  }

  private static CraftingRecipe display(
      String name, ItemStack output, ItemStack remainder, ItemStack... inputs) {
    DefaultedList<Ingredient> ingredients = DefaultedList.of();
    for (ItemStack input : inputs) ingredients.add(Ingredient.ofStacks(input.copy()));
    return new DisplayRecipe(
        new Identifier("icbm_port", "jei/" + name), output.copy(), ingredients, remainder.copy());
  }

  /** Display-only recipe; never inserted into the server recipe manager. */
  public static final class DisplayRecipe extends ShapelessRecipe {
    private final ItemStack remainder;

    DisplayRecipe(
        Identifier id,
        ItemStack output,
        DefaultedList<Ingredient> ingredients,
        ItemStack remainder) {
      super(id, "icbm_port", output, ingredients);
      this.remainder = remainder;
    }

    @Override
    public DefaultedList<ItemStack> getRemainder(CraftingInventory inventory) {
      DefaultedList<ItemStack> result = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
      if (!remainder.isEmpty())
        for (int i = 0; i < inventory.size(); i++)
          if (!inventory.getStack(i).isEmpty()) {
            result.set(i, remainder.copy());
            break;
          }
      return result;
    }
  }
}
