package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.api.genetic.AgriAllele;
import com.agricraft.agricraft.api.genetic.AgriGene;
import com.agricraft.agricraft.api.genetic.AgriGenePair;
import com.agricraft.agricraft.api.genetic.AgriGeneRegistry;
import com.agricraft.agricraft.api.genetic.AgriGenome;
import com.agricraft.agricraft.api.stat.AgriStat;
import com.agricraft.agricraft.api.stat.AgriStatRegistry;
import com.agricraft.agricraft.common.block.CropBlock;
import com.agricraft.agricraft.common.block.CropState;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.item.TrowelItem;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;
import static com.agricraft.agricraft.gametest.GameTestSupport.crop;
import static com.agricraft.agricraft.gametest.GameTestSupport.cropSticks;
import static com.agricraft.agricraft.gametest.GameTestSupport.genome;
import static com.agricraft.agricraft.gametest.GameTestSupport.matureCrop;

/** Crop sticks, growth, cross-breeding and stat inheritance with the shipped vanilla plant data. */
public class CropGameTests {

	@GameTest(template = TEMPLATE)
	public static void plantDataLoadsWithTheServer(GameTestHelper helper) {
		check(AgriApi.getPlant(GameTestSupport.id("minecraft:wheat")).isPresent(), "minecraft:wheat plant is not loaded");
		check(AgriApi.getSoilRegistry().map(registry -> registry.containsKey(GameTestSupport.id("minecraft:farmland"))).orElse(false), "farmland soil is not loaded");
		check(AgriApi.getMutationsFromParents("minecraft:wheat", "minecraft:potato").anyMatch(m -> m.child().toString().equals("minecraft:carrot")),
				"wheat + potato -> carrot mutation is not loaded");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void seedPlantsOnCropSticksAndGrows(GameTestHelper helper) {
		BlockPos soil = new BlockPos(1, 1, 1);
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);
		// resistance 10 keeps weeds away so only plant growth ticks run
		ItemStack seed = AgriSeedItem.toStack(genome("minecraft:wheat", 10));
		seed.setCount(2);
		Player player = helper.makeMockPlayer();
		player.setItemInHand(InteractionHand.MAIN_HAND, seed);
		BlockPos cropPos = helper.absolutePos(soil.above());
		seed.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(cropPos), Direction.UP, cropPos, false)));
		check(crop.hasPlant(), "the seed was not planted on the crop sticks");
		check("minecraft:wheat".equals(crop.getPlantId()), "planted " + crop.getPlantId() + " instead of minecraft:wheat");
		// GameTestHelper's mock player is in creative mode, which keeps the seed
		int expected = player.isCreative() ? 2 : 1;
		check(seed.getCount() == expected, "planting left " + seed.getCount() + " seeds, expected " + expected);
		check(helper.getBlockState(soil.above()).getValue(CropBlock.CROP_STATE) == CropState.PLANT_STICKS, "crop sticks did not switch to the planted state");
		// the light engine relights the freshly placed test area off-thread, so wait for daylight to reach the crop
		helper.startSequence()
				.thenWaitUntil(() -> check(crop.isFertile(), "wheat on wet farmland in daylight should be fertile: " + crop.getFertilityResponse()))
				.thenExecute(() -> {
					int ticks = 0;
					while (!crop.isFullyGrown() && ticks < 2000) {
						crop.applyGrowthTick();
						ticks++;
					}
					check(crop.isFullyGrown(), "wheat did not reach its final stage after 2000 growth ticks (stage " + crop.getGrowthStage().index() + ")");
					check(!crop.hasWeeds(), "weeds grew on a resistance-10 plant");
					check(helper.getBlockState(soil).is(Blocks.FARMLAND), "farmland under crop sticks turned into dirt");
				})
				.thenSucceed();
	}

	@GameTest(template = TEMPLATE)
	public static void crossBetweenParentsProducesExpectedMutation(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.WATER);
		matureCrop(helper, new BlockPos(1, 1, 2), "minecraft:wheat", 10);
		matureCrop(helper, new BlockPos(3, 1, 2), "minecraft:potato", 10);
		BlockPos center = new BlockPos(2, 1, 2);
		Set<String> seen = new HashSet<>();
		boolean mutated = false;
		for (int attempt = 0; attempt < 200 && !mutated; attempt++) {
			AgriCrop cross = cropSticks(helper, center, CropState.DOUBLE_STICKS);
			check(cross.isCrossCropSticks(), "double crop sticks are not cross crop sticks");
			for (int tick = 0; tick < 50 && !cross.hasPlant(); tick++) {
				AgriApi.getMutationHandler().getActiveCrossBreedEngine().handleCrossBreedTick(cross, ((com.agricraft.agricraft.common.block.entity.CropBlockEntity) cross).streamNeighbours(), helper.getLevel().random);
			}
			if (cross.hasPlant()) {
				seen.add(cross.getPlantId());
				mutated = cross.getPlantId().equals("minecraft:carrot");
				check(!cross.isCrossCropSticks(), "the child should replace the cross sticks with planted sticks");
			}
		}
		check(mutated, "200 crosses of wheat and potato never produced the carrot mutation (saw " + seen + ")");
		seen.removeAll(Set.of("minecraft:wheat", "minecraft:potato", "minecraft:carrot"));
		check(seen.isEmpty(), "crossing wheat and potato produced unrelated plants: " + seen);
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void statInheritanceStaysInBounds(GameTestHelper helper) {
		RandomSource random = RandomSource.create(42L);
		AgriCrop crop = cropSticks(helper, new BlockPos(1, 1, 1), CropState.DOUBLE_STICKS);
		for (AgriStat stat : AgriStatRegistry.getInstance()) {
			AgriGene<Integer> gene = AgriGeneRegistry.getInstance().getGeneStat(stat).orElseThrow();
			for (int low = stat.getMin(); low <= stat.getMax(); low++) {
				int high = Math.min(stat.getMax(), low + 3);
				AgriGenome parent1 = genome("minecraft:wheat", low);
				AgriGenome parent2 = genome("minecraft:wheat", high);
				for (int i = 0; i < 50; i++) {
					AgriAllele<Integer> first = gene.getAllele(low);
					AgriAllele<Integer> second = gene.getAllele(high);
					AgriGenePair<Integer> child = gene.mutator().pickOrMutate(crop, gene, first, second, parent1, parent2, random);
					for (int trait : new int[]{child.getDominant().trait(), child.getRecessive().trait()}) {
						check(trait >= stat.getMin() && trait <= stat.getMax(),
								stat.getId() + " allele " + trait + " is outside [" + stat.getMin() + ", " + stat.getMax() + "]");
						check(trait >= low - 1 && trait <= high + 1,
								stat.getId() + " allele " + trait + " moved more than one step from parents " + low + "/" + high);
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void harvestDropsProductsAndKeepsThePlant(GameTestHelper helper) {
		AgriCrop crop = matureCrop(helper, new BlockPos(1, 1, 1), "minecraft:wheat", 10);
		check(crop.canBeHarvested(), "a fully grown wheat crop should be harvestable");
		java.util.List<ItemStack> products = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			crop.getHarvestProducts(products::add);
		}
		check(products.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.WHEAT)), "20 harvests of wheat produced no wheat: " + products);
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void emptyCropSticksGrowWeeds(GameTestHelper helper) {
		AgriCrop crop = cropSticks(helper, new BlockPos(1, 1, 1), CropState.SINGLE_STICKS);
		for (int tick = 0; tick < 2000 && !crop.hasWeeds(); tick++) {
			crop.applyGrowthTick();
		}
		check(crop.hasWeeds(), "empty crop sticks grew no weeds in 2000 growth ticks");
		crop.removeWeeds();
		check(!crop.hasWeeds(), "removeWeeds left weeds behind");
		check(crop(helper, new BlockPos(1, 2, 1)).hasCropSticks(), "removing weeds removed the crop sticks");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void trowelMovesPlantsWithoutOverwritingBlocks(GameTestHelper helper) {
		AgriCrop source = matureCrop(helper, new BlockPos(1, 1, 1), "minecraft:wheat", 3);
		ItemStack trowel = new ItemStack(ModItems.TROWEL.get());
		TrowelItem item = (TrowelItem) trowel.getItem();
		Player player = helper.makeMockPlayer();
		player.setItemInHand(InteractionHand.MAIN_HAND, trowel);
		useOn(helper, player, trowel, new BlockPos(1, 2, 1));
		check(!source.hasPlant() && item.hasPlant(trowel), "the trowel should pick the plant up");
		// a block above the clicked soil is never replaced
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		useOn(helper, player, trowel, new BlockPos(3, 1, 3));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 3));
		check(item.hasPlant(trowel), "the trowel should keep its plant when the space above the soil is taken");
		// a block that is not a soil gets no plant
		helper.setBlock(new BlockPos(5, 1, 1), Blocks.STONE);
		useOn(helper, player, trowel, new BlockPos(5, 1, 1));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 1));
		check(item.hasPlant(trowel), "the trowel should keep its plant when the clicked block is not a soil");
		// empty space above a soil takes the plant
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.FARMLAND);
		useOn(helper, player, trowel, new BlockPos(5, 1, 5));
		AgriCrop planted = crop(helper, new BlockPos(5, 2, 5));
		check("minecraft:wheat".equals(planted.getPlantId()) && !item.hasPlant(trowel), "the trowel should plant into the empty space above a soil");
		helper.succeed();
	}

	private static void useOn(GameTestHelper helper, Player player, ItemStack stack, BlockPos relative) {
		BlockPos abs = helper.absolutePos(relative);
		stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
	}

}
