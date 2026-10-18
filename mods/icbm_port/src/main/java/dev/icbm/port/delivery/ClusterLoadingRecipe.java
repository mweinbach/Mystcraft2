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

/** One cluster plus bomblets appends one droplet per occupied crafting slot. */
public final class ClusterLoadingRecipe extends SpecialCraftingRecipe {
  public ClusterLoadingRecipe(Identifier id) {
    super(id);
  }

  @Override
  public boolean matches(CraftingInventory inventory, World world) {
    return !craft(inventory).isEmpty();
  }

  @Override
  public ItemStack craft(CraftingInventory inventory) {
    ItemStack cluster = ItemStack.EMPTY;
    List<PayloadType> additions = new ArrayList<>();
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getStack(i);
      if (stack.isEmpty()) continue;
      if (stack.isOf(DeliveryContent.CLUSTER_MISSILE)) {
        if (!cluster.isEmpty()) return ItemStack.EMPTY;
        cluster = stack;
      } else {
        PayloadType payload = DeliveryContent.payloadForBomblet(stack);
        if (payload == null) return ItemStack.EMPTY;
        additions.add(payload);
      }
    }
    if (cluster.isEmpty() || additions.isEmpty()) return ItemStack.EMPTY;
    List<PayloadType> payloads = new ArrayList<>(ClusterPlan.fromStack(cluster).payloads());
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
