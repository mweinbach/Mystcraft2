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

  /** Items whose viewer identity depends on serialized payload/cargo NBT. */
  public static List<Item> payloadItems() {
    return List.of(
        DeliveryContent.CLUSTER_MISSILE,
        DeliveryContent.HELD_ITEM_MISSILE,
        DeliveryContent.CARGO_PARACHUTE,
        DeliveryContent.CARGO_BALLOON);
  }

  /** One usage page shared by every recipe-viewer adapter. */
  public record InfoPage(String id, Item item, String text) {}

  public static List<InfoPage> infoPages() {
    String carrier =
        "Craft with one permitted item to store cargo; craft the loaded carrier alone to retrieve"
            + " it and retain the empty carrier. Sneak-use cycles ITEM/BLOCK/ENTITY/PROJECTILE"
            + " mode. Use on an entity to attach a passenger. Server cargo restrictions apply.";
    return List.of(
        new InfoPage(
            "cluster",
            DeliveryContent.CLUSTER_MISSILE,
            "Craft an empty cluster carrier with tier-one bomblets to load a mixed payload. Add more"
                + " bomblets in later crafts, up to 90 with default space settings. An empty"
                + " carrier cannot launch."),
        new InfoPage(
            "held_item",
            DeliveryContent.HELD_ITEM_MISSILE,
            "Craft the empty casing with one permitted item. Craft a loaded casing alone to retrieve"
                + " its item and keep the empty casing. Sneak-use cycles primary/secondary action"
                + " order. Both handheld and block launchers accept loaded missiles."),
        new InfoPage("parachute", DeliveryContent.CARGO_PARACHUTE, carrier),
        new InfoPage("balloon", DeliveryContent.CARGO_BALLOON, carrier));
  }

  public static String subtype(ItemStack stack) {
    return subtype(stack.getItem(), stack.getNbt());
  }

  /**
   * Pure serialized identity: never constructs a nested ItemStack or runs third-party item hooks.
   */
  public static String subtype(Item item, net.minecraft.nbt.NbtCompound source) {
    net.minecraft.nbt.NbtCompound nbt =
        source == null ? new net.minecraft.nbt.NbtCompound() : source.copy();
    if (item == DeliveryContent.CLUSTER_MISSILE) {
      var payloads = ClusterPlan.read(nbt).payloads();
      return payloads.isEmpty() ? "" : payloads.toString();
    }
    if (item == DeliveryContent.HELD_ITEM_MISSILE) {
      String mode =
          normalizedMode(
              nbt.getString("HeldAction"), HeldItemMissileItem.Mode.values(), "PRIMARY_FIRST");
      String cargo = serializedCargo(nbt.getCompound("HeldItem"));
      return cargo.isEmpty() && mode.equals("PRIMARY_FIRST") ? "" : mode + "|" + cargo;
    }
    if (item == DeliveryContent.CARGO_PARACHUTE || item == DeliveryContent.CARGO_BALLOON) {
      String mode = normalizedMode(nbt.getString("CarrierMode"), CarrierData.Mode.values(), "ITEM");
      String cargo = serializedCargo(nbt.getCompound("CarrierCargo"));
      return cargo.isEmpty() && mode.equals("ITEM") ? "" : mode + "|" + cargo;
    }
    return "";
  }

  private static String normalizedMode(String value, Enum<?>[] modes, String fallback) {
    for (Enum<?> mode : modes) if (mode.name().equals(value)) return value;
    return fallback;
  }

  private static String serializedCargo(net.minecraft.nbt.NbtCompound cargo) {
    Identifier id = Identifier.tryParse(cargo.getString("id"));
    if (cargo.getByte("Count") <= 0
        || id == null
        || !Registry.ITEM.containsId(id)
        || id.equals(new Identifier("minecraft", "air"))) return "";
    net.minecraft.nbt.NbtCompound identity = cargo.copy();
    identity.putByte("Count", (byte) 1);
    if (identity.getCompound("tag").isEmpty()) identity.remove("tag");
    return canonicalNbt(identity);
  }

  private static String canonicalNbt(net.minecraft.nbt.NbtElement element) {
    if (element instanceof net.minecraft.nbt.NbtCompound compound) {
      StringBuilder result = new StringBuilder("{");
      compound.getKeys().stream()
          .sorted()
          .forEach(
              key ->
                  result
                      .append(net.minecraft.nbt.NbtString.of(key))
                      .append(':')
                      .append(canonicalNbt(compound.get(key)))
                      .append(','));
      return result.append('}').toString();
    }
    if (element instanceof net.minecraft.nbt.NbtList list) {
      StringBuilder result = new StringBuilder("[");
      for (var entry : list) result.append(canonicalNbt(entry)).append(',');
      return result.append(']').toString();
    }
    return element == null ? "null" : element.toString();
  }

  public static List<CraftingRecipe> all() {
    List<CraftingRecipe> recipes = new ArrayList<>();
    for (Item item : Registry.ITEM) {
      if (item == Items.AIR) continue;
      // Preserve item-defined defaults (notably PotionItem's water Potion NBT).
      // A bare constructor can inject invalid subtype-less stacks into a viewer's global index.
      ItemStack defaultStack = item.getDefaultStack().copy();
      if (defaultStack.isEmpty()) continue;
      defaultStack.setCount(1);
      recipes.addAll(forCargo(defaultStack));
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

    /** Immutable display metadata for native viewers; not an extra recipe output. */
    public ItemStack displayRemainder() {
      return remainder.copy();
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
