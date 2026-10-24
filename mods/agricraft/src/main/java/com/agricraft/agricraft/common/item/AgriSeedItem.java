package com.agricraft.agricraft.common.item;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import java.util.Comparator;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.genetic.AgriGenomeProviderItem;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.api.genetic.AgriGenome;
import com.agricraft.agricraft.common.block.entity.SeedAnalyzerBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlocks;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.common.util.LangUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class AgriSeedItem extends BlockItem implements AgriGenomeProviderItem {

	public AgriSeedItem(Properties properties) {
		super(ModBlocks.CROP.get(), properties);
	}

	/**
	 * Create an ItemStack with the default genome for a plant
	 *
	 * @param plant the plant to create to genome from
	 * @return an ItemStack with the default genome of the plant
	 */
	public static ItemStack toStack(AgriPlant plant) {
		ItemStack stack = new ItemStack(ModItems.SEED.get(), 1);
		AgriGenome genome = new AgriGenome(plant);
		CompoundTag tag = new CompoundTag();
		genome.writeToNBT(tag);
		stack.setTag(tag);
		return stack;
	}

	/**
	 * Create an ItemStack with the given genome
	 *
	 * @param genome the genome to create the ItemStack from
	 * @return an ItemStack with the given genome
	 */
	public static ItemStack toStack(AgriGenome genome) {
		ItemStack stack = new ItemStack(ModItems.SEED.get(), 1);
		CompoundTag tag = new CompoundTag();
		genome.writeToNBT(tag);
		stack.setTag(tag);
		return stack;
	}

	/**
	 * Compute the plant species from an ItemStack
	 *
	 * @param stack the itemstack to compute the species from
	 * @return the plant species formatted as a resource location, or {@code agricraft:unknown} if not found
	 */
	public static String getSpecies(ItemStack stack) {
		if (stack.getItem() != ModItems.SEED.get()) {
			return "agricraft:unknown";
		}
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return "agricraft:unknown";
		}
		AgriGenome genome = AgriGenome.fromNBT(tag);
		if (genome == null) {
			return "agricraft:unknown";
		}
		return genome.getSpeciesGene().getDominant().trait();
	}

	@Override
	public Component getName(ItemStack stack) {
		if (stack.getTag() == null) {
			return Component.translatable("seed.agricraft.agricraft.unknown");
		}
		AgriGenome genome = AgriGenome.fromNBT(stack.getTag());
		if (genome == null) {
			return Component.translatable("seed.agricraft.agricraft.unknown");
		}
		return LangUtils.seedName(genome.getSpeciesGene().getDominant().trait());
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		InteractionResult result = super.place(context);
		Level level = context.getLevel();
		if (result.consumesAction() && !level.isClientSide) {
			AgriApi.getCrop(level, context.getClickedPos()).ifPresent(crop -> {
				CompoundTag tag = context.getItemInHand().getTag();
				if (tag != null) {
					crop.plantGenome(AgriGenome.fromNBT(tag));
				}
			});
		}
		return result;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide) {
			return InteractionResult.PASS;
		}
		ItemStack heldItem = context.getItemInHand();
		// if crop sticks were clicked, attempt to plant the seed
		Optional<AgriCrop> optionalAgriCrop = AgriApi.getCrop(level, context.getClickedPos());
		if (optionalAgriCrop.isPresent()) {
			AgriCrop crop = optionalAgriCrop.get();
			if (crop.hasPlant() || crop.isCrossCropSticks()) {
				return InteractionResult.PASS;
			}
			plantSeed(context.getPlayer(), crop, heldItem);
			return InteractionResult.CONSUME;
		}
		// if a seed analyzer was clicked, insert the seed inside
		if (level.getBlockEntity(context.getClickedPos()) instanceof SeedAnalyzerBlockEntity seedAnalyzer) {
			if (seedAnalyzer.hasSeed()) {
				return InteractionResult.PASS;
			}
			ItemStack remaining = seedAnalyzer.insertSeed(heldItem);
			heldItem.setCount(remaining.getCount());
			return InteractionResult.CONSUME;
		}
		// if a soil with a crop above was clicked, plant in that crop
		if (AgriApi.getSoil(level, context.getClickedPos()).isPresent()) {
			Optional<AgriCrop> above = AgriApi.getCrop(level, context.getClickedPos().above());
			if (above.isPresent()) {
				AgriCrop crop = above.get();
				// there is a crop with a plant or is a cross crop stick, do nothing
				if (crop.hasPlant() || crop.isCrossCropSticks()) {
					return InteractionResult.PASS;
				}
				plantSeed(context.getPlayer(), crop, heldItem);
				return InteractionResult.CONSUME;
			}
		}
		// 4.0.6 built this fallback with orElse(super.useOn(context)), which placed the seed as a block before the
		// soil and crop checks above ran; only reach it when nothing else handled the click
		return super.useOn(context);
	}

	private void plantSeed(Player player, AgriCrop crop, ItemStack seed) {
		crop.plantGenome(AgriGenome.fromNBT(seed.getTag()), player);
		if (player != null && !player.isCreative()) {
			seed.shrink(1);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
		CompoundTag tag = stack.getTag();
		if (tag != null) {
			AgriGenome genome = AgriGenome.fromNBT(tag);
			if (genome != null) {
				genome.appendHoverText(tooltipComponents, isAdvanced);
			}
		}
	}

	/**
	 * The seeds tab and the creative search list one seed per loaded plant, sorted by id, as upstream's seeds tab
	 * does (1.19.2 tabs are filled per item rather than by a display callback).
	 */
	@Override
	public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
		if (this.allowedIn(tab)) {
			AgriApi.getPlantRegistry().ifPresent(registry -> registry.entrySet().stream()
					.sorted(Comparator.comparing(entry -> entry.getKey().location()))
					.forEach(entry -> items.add(toStack(entry.getValue()))));
		}
	}

//	@Override
//	public ItemStack getDefaultInstance() {
//		return AgriApi.getPlant(new ResourceLocation("minecraft:wheat")).map(AgriSeedItem::toStack).orElse(super.getDefaultInstance());
//	}

}
