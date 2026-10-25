package com.agricraft.agricraft.common.item.crafting;

import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.common.registry.ModRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom recipe to add the magnifying property to a helmet, crafted from the helmet and a magnifying glass.
 * An item is considered as a helmet if it is an instance of {@link  ArmorItem} and his equipment slot is {@link  EquipmentSlot#HEAD}
 */
public class MagnifyingHelmetRecipe extends CustomRecipe {

	public MagnifyingHelmetRecipe(ResourceLocation id) {
		super(id);
	}

	@Override
	public boolean matches(CraftingContainer container, Level level) {
		boolean helmet = false;
		boolean glass = false;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack itemStack = container.getItem(i);
			if (itemStack.getItem() instanceof ArmorItem armorItem && armorItem.getSlot() == EquipmentSlot.HEAD && (itemStack.getTag() == null || !itemStack.getTag().getBoolean("magnifying"))) {
				if (helmet) {
					return false;
				} else {
					helmet = true;
				}
			} else if (itemStack.is(ModItems.MAGNIFYING_GLASS.get())) {
				if (glass) {
					return false;
				} else {
					glass = true;
				}
			} else if (!itemStack.isEmpty()) {
				// taking the result consumes one item from every occupied slot, so anything else must not match
				return false;
			}
		}
		return helmet && glass;
	}

	@NotNull
	@Override
	public ItemStack assemble(CraftingContainer container) {
		ItemStack helmet = null;
		ItemStack glass = null;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack itemStack = container.getItem(i);
			if (itemStack.getItem() instanceof ArmorItem armorItem && armorItem.getSlot() == EquipmentSlot.HEAD) {
				helmet = itemStack;
			} else if (itemStack.is(ModItems.MAGNIFYING_GLASS.get())) {
				glass = itemStack;
			}
		}
		if (helmet != null && glass != null) {
			ItemStack copy = helmet.copy();
			copy.getOrCreateTag().putBoolean("magnifying", true);
			return copy;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@NotNull
	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.MAGNIFYING_HELMET.get();
	}

}
