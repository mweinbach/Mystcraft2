/*
 * ProjectE 1.19.2 port - EMI integration, shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 *
 * Shows the same two recipe categories upstream's JEI plugin (PEJeiPlugin) adds, for packs such as Tekxit 4 that use
 * EMI instead of JEI.
 */
package moze_intel.projecte.integration.emi;

import com.mojang.datafixers.util.Either;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import moze_intel.projecte.PECore;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.integration.jei.world_transmute.WorldTransmuteEntry;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.WorldTransmutations;
import moze_intel.projecte.utils.text.ILangEntry;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Registered through the {@code emi} entrypoint on Fabric and {@link EmiEntrypoint} on Forge.
 */
@EmiEntrypoint
public class PEEmiPlugin implements EmiPlugin {

	public static final EmiRecipeCategory WORLD_TRANSMUTATION = new Category("world_transmutation", PEItems.PHILOSOPHERS_STONE.asItem(),
			PELang.WORLD_TRANSMUTE);
	public static final EmiRecipeCategory COLLECTOR = new Category("collector", PEBlocks.COLLECTOR.asItem(), PELang.JEI_COLLECTOR);

	@Override
	public void register(EmiRegistry registry) {
		registry.addCategory(WORLD_TRANSMUTATION);
		registry.addCategory(COLLECTOR);

		registry.addWorkstation(WORLD_TRANSMUTATION, EmiStack.of(PEItems.PHILOSOPHERS_STONE));
		registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(PEItems.PHILOSOPHERS_STONE));
		registry.addWorkstation(COLLECTOR, EmiStack.of(PEBlocks.COLLECTOR));
		registry.addWorkstation(COLLECTOR, EmiStack.of(PEBlocks.COLLECTOR_MK2));
		registry.addWorkstation(COLLECTOR, EmiStack.of(PEBlocks.COLLECTOR_MK3));
		for (EmiRecipeCategory category : List.of(VanillaEmiRecipeCategories.SMELTING, VanillaEmiRecipeCategories.FUEL)) {
			registry.addWorkstation(category, EmiStack.of(PEBlocks.DARK_MATTER_FURNACE));
			registry.addWorkstation(category, EmiStack.of(PEBlocks.RED_MATTER_FURNACE));
		}

		int index = 0;
		for (WorldTransmuteEntry entry : worldTransmutations()) {
			EmiIngredient input = entry.getInput().map(PEEmiPlugin::stack).orElse(EmiStack.EMPTY);
			List<EmiStack> outputs = new ArrayList<>();
			entry.getOutput().forEach(output -> outputs.add(stack(output)));
			registry.addRecipe(new WorldTransmuteEmiRecipe(PECore.rl("/world_transmutation/" + index++), input, outputs));
		}
		index = 0;
		for (Item item : FuelMapper.getFuelMap()) {
			ItemStack stack = new ItemStack(item);
			ItemStack upgrade = FuelMapper.getFuelUpgrade(stack);
			long upgradeEmc = EMCHelper.getEmcValue(upgrade) - EMCHelper.getEmcValue(stack);
			// Same filter as upstream's JEI plugin: only upgrades that do not lose EMC.
			if (upgradeEmc >= 0) {
				registry.addRecipe(new CollectorEmiRecipe(PECore.rl("/collector/" + index++), stack, upgrade, upgradeEmc));
			}
		}
	}

	/**
	 * Upstream's JEI list (WorldTransmuteRecipeCategory.getAllTransmutations): every renderable entry, one per input.
	 */
	static List<WorldTransmuteEntry> worldTransmutations() {
		List<WorldTransmuteEntry> visible = new ArrayList<>();
		WorldTransmutations.getWorldTransmutations().forEach(transmutation -> {
			WorldTransmuteEntry entry = new WorldTransmuteEntry(transmutation);
			if (!entry.isRenderable()) {
				return;
			}
			boolean alreadyHas;
			FluidStack inputFluid = entry.getInputFluid();
			if (inputFluid.isEmpty()) {
				ItemStack inputItem = entry.getInputItem();
				alreadyHas = visible.stream().map(WorldTransmuteEntry::getInputItem).anyMatch(other -> !other.isEmpty() && inputItem.sameItem(other));
			} else {
				alreadyHas = visible.stream().map(WorldTransmuteEntry::getInputFluid).anyMatch(other -> !other.isEmpty() && inputFluid.isFluidEqual(other));
			}
			if (!alreadyHas) {
				visible.add(entry);
			}
		});
		return visible;
	}

	private static EmiStack stack(Either<ItemStack, FluidStack> either) {
		return either.map(EmiStack::of, fluid -> EmiStack.of(fluid.getFluid()));
	}

	private static class Category extends EmiRecipeCategory {

		private final ILangEntry name;

		Category(String path, Item icon, ILangEntry name) {
			super(PECore.rl(path), EmiStack.of(icon));
			this.name = name;
		}

		@Override
		public Component getName() {
			return name.translate();
		}
	}

	static ResourceLocation arrowTexture() {
		return PECore.rl("textures/gui/arrow.png");
	}
}
