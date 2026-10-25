package dev.icbm.port.delivery;

import dev.icbm.port.payload.PayloadType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * One cluster plus bomblets appends one droplet per occupied crafting slot.
 *
 * <p>Volume rule adapted from MIT ICBM-Classic {@code content/cluster/missile/RecipeCluster.java}:
 * the stored contents plus every item in the grid (including the carrier itself, an upstream
 * quirk that reserves 20 of the 200 default volume) must not exceed the configured maximum, and
 * every added item must pass the cluster ban/allow list. Only tier-one bomblets are loadable here
 * (see PARITY.md for arbitrary-item cluster payloads).
 */
public final class ClusterLoadingRecipe extends SpecialCraftingRecipe {
  public ClusterLoadingRecipe(Identifier id) {
    super(id);
  }

  /** Total volume of a carrier's stored droplets. */
  public static int storedVolume(ClusterPlan plan) {
    int volume = 0;
    for (PayloadType payload : plan.payloads()) {
      var item = DeliveryContent.BOMBLETS.get(payload);
      if (item != null) volume += DeliveryConfig.clusterSize(new ItemStack(item));
    }
    return volume;
  }

  @Override
  public boolean matches(CraftingInventory inventory, World world) {
    return !craft(inventory).isEmpty();
  }

  @Override
  public ItemStack craft(CraftingInventory inventory) {
    ItemStack cluster = ItemStack.EMPTY;
    List<PayloadType> additions = new ArrayList<>();
    int newVolume = 0;
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getStack(i);
      if (stack.isEmpty()) continue;
      if (stack.isOf(DeliveryContent.CLUSTER_MISSILE)) {
        if (!cluster.isEmpty()) return ItemStack.EMPTY;
        cluster = stack;
      } else {
        PayloadType payload = DeliveryContent.payloadForBomblet(stack);
        if (payload == null || !DeliveryConfig.clusterAllowed(stack)) return ItemStack.EMPTY;
        additions.add(payload);
      }
      newVolume += DeliveryConfig.clusterSize(stack);
    }
    if (cluster.isEmpty() || additions.isEmpty()) return ItemStack.EMPTY;
    ClusterPlan current = ClusterPlan.fromStack(cluster);
    if (storedVolume(current) + newVolume > DeliveryConfig.clusterMaxVolume())
      return ItemStack.EMPTY;
    List<PayloadType> payloads = new ArrayList<>(current.payloads());
    if (payloads.size() + additions.size() > ClusterPlan.MAX_BOMBLETS) return ItemStack.EMPTY;
    payloads.addAll(additions);
    ItemStack result = cluster.copy();
    result.setCount(1);
    new ClusterPlan(payloads).writeTo(result);
    return result;
  }

  @Override
  public boolean fits(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return DeliveryContent.CLUSTER_LOADING;
  }

  @Override
  public DefaultedList<ItemStack> getRemainder(CraftingInventory inventory) {
    return DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
  }
}
