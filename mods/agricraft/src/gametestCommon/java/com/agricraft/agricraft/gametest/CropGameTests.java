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
import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.item.SeedBagItem;
import com.agricraft.agricraft.common.item.crafting.MagnifyingHelmetRecipe;
import com.agricraft.agricraft.common.item.TrowelItem;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import com.agricraft.agricraft.common.registry.ModBlocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedstoneLampBlock;
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
	public static void grownNitorWartRaisesItsLight(GameTestHelper helper) {
		BlockPos soil = new BlockPos(1, 1, 1);
		BlockPos cropPos = soil.above();
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);
		crop.plantGenome(genome("agricraft:nitor_wart", 10));
		check(crop.hasPlant(), "nitor wart was not planted");
		int initial = helper.getBlockState(cropPos).getValue(CropBlock.LIGHT);
		while (!crop.isFullyGrown()) {
			crop.setGrowthStage(crop.getGrowthStage().getNext(crop, helper.getLevel().random));
		}
		// read the state from the level: the light engine uses the installed state, not the block entity
		BlockState grown = helper.getBlockState(cropPos);
		int light = grown.getValue(CropBlock.LIGHT);
		check(light > initial, "grown nitor wart LIGHT " + light + " is not above its initial " + initial);
		check(light == 15, "grown nitor wart LIGHT is " + light + ", expected 15");
		check(grown.getLightEmission() == 15, "grown nitor wart emits light " + grown.getLightEmission() + ", expected 15");
		crop.removeGenome();
		check(helper.getBlockState(cropPos).getValue(CropBlock.LIGHT) == 0, "crop sticks kept glowing after the nitor wart was removed");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void grownRedstodendronEmitsASignal(GameTestHelper helper) {
		BlockPos soil = new BlockPos(2, 1, 2);
		BlockPos cropPos = soil.above();
		BlockPos lamp = cropPos.east();
		BlockPos wall = cropPos.south();
		BlockPos lampBehindWall = wall.south();
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(lampBehindWall, Blocks.REDSTONE_LAMP);
		crop.plantGenome(genome("agricraft:redstodendron", 10));
		check(crop.hasPlant(), "redstodendron was not planted");
		BlockPos absolute = helper.absolutePos(cropPos);
		int initial = helper.getLevel().getBlockState(absolute).getDirectSignal(helper.getLevel(), absolute, Direction.WEST);
		while (!crop.isFullyGrown()) {
			crop.setGrowthStage(crop.getGrowthStage().getNext(crop, helper.getLevel().random));
		}
		BlockState grown = helper.getLevel().getBlockState(absolute);
		check(grown.isSignalSource(), "a planted redstodendron is not a signal source");
		int signal = grown.getSignal(helper.getLevel(), absolute, Direction.WEST);
		int direct = grown.getDirectSignal(helper.getLevel(), absolute, Direction.WEST);
		check(signal > 0 && direct > 0, "grown redstodendron emits signal " + signal + " / direct " + direct + ", expected > 0");
		check(direct > initial, "redstodendron power " + direct + " did not rise with growth from " + initial);
		check(helper.getLevel().getSignal(helper.absolutePos(lamp), Direction.EAST) > 0, "the block next to the crop is not powered");
		// growth must update neighbours: the lamp beside the crop and the one behind the strongly powered stone light up
		helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
		helper.assertBlockProperty(lampBehindWall, RedstoneLampBlock.LIT, true);
		helper.succeed();
	}

	/** Sets up a mature redstodendron at (2,2,2) with a lamp beside it and a lamp behind a stone block it powers. */
	private static AgriCrop poweredRedstodendron(GameTestHelper helper, BlockPos cropPos) {
		AgriCrop crop = cropSticks(helper, cropPos.below(), CropState.SINGLE_STICKS);
		helper.setBlock(cropPos.east(), Blocks.REDSTONE_LAMP);
		helper.setBlock(cropPos.south(), Blocks.STONE);
		helper.setBlock(cropPos.south().south(), Blocks.REDSTONE_LAMP);
		crop.plantGenome(genome("agricraft:redstodendron", 10));
		while (!crop.isFullyGrown()) {
			crop.setGrowthStage(crop.getGrowthStage().getNext(crop, helper.getLevel().random));
		}
		helper.assertBlockProperty(cropPos.east(), RedstoneLampBlock.LIT, true);
		helper.assertBlockProperty(cropPos.south().south(), RedstoneLampBlock.LIT, true);
		return crop;
	}

	private static void lampsGoOff(GameTestHelper helper, BlockPos cropPos, String what) {
		// a lamp turns off 4 ticks after it loses power
		helper.startSequence()
				.thenWaitUntil(() -> {
					check(!helper.getBlockState(cropPos.east()).getValue(RedstoneLampBlock.LIT), "the lamp beside the crop stayed lit after " + what);
					check(!helper.getBlockState(cropPos.south().south()).getValue(RedstoneLampBlock.LIT), "the lamp behind the stone stayed lit after " + what);
				})
				.thenSucceed();
	}

	@GameTest(template = TEMPLATE)
	public static void breakingAPoweredCropTurnsTheLampsOff(GameTestHelper helper) {
		BlockPos cropPos = new BlockPos(2, 2, 2);
		poweredRedstodendron(helper, cropPos);
		helper.getLevel().destroyBlock(helper.absolutePos(cropPos), true);
		lampsGoOff(helper, cropPos, "the crop was broken");
	}

	@GameTest(template = TEMPLATE)
	public static void aPoweredCropLosingItsSoilTurnsTheLampsOff(GameTestHelper helper) {
		BlockPos cropPos = new BlockPos(2, 2, 2);
		poweredRedstodendron(helper, cropPos);
		helper.setBlock(cropPos.below(), Blocks.AIR);
		check(!helper.getBlockState(cropPos).is(ModBlocks.CROP.get()), "the crop survived without soil");
		lampsGoOff(helper, cropPos, "the soil was removed");
	}

	@GameTest(template = TEMPLATE)
	public static void removingAReloadedPoweredPlantTurnsTheLampsOff(GameTestHelper helper) {
		BlockPos cropPos = new BlockPos(2, 2, 2);
		poweredRedstodendron(helper, cropPos);
		// reload the block entity from its saved tag, as a chunk load does
		BlockPos absolute = helper.absolutePos(cropPos);
		CompoundTag saved = helper.getLevel().getBlockEntity(absolute).saveWithFullMetadata();
		helper.getLevel().removeBlockEntity(absolute);
		BlockEntity reloaded = BlockEntity.loadStatic(absolute, helper.getLevel().getBlockState(absolute), saved);
		check(reloaded != null, "the crop block entity did not load from its tag");
		helper.getLevel().setBlockEntity(reloaded);
		AgriCrop crop = crop(helper, cropPos);
		check(crop.hasPlant() && crop.isFullyGrown(), "the reloaded crop lost its plant or growth");
		crop.removeGenome();
		lampsGoOff(helper, cropPos, "the reloaded plant was removed");
	}

	@GameTest(template = TEMPLATE)
	public static void newWeedsAreSavedAndReload(GameTestHelper helper) {
		BlockPos soil = new BlockPos(1, 1, 1);
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);
		BlockPos absolute = helper.absolutePos(soil.above());
		LevelChunk chunk = helper.getLevel().getChunkAt(absolute);
		chunk.setUnsaved(false);
		String weedId = "agricraft:weed";
		crop.setWeed(weedId, AgriApi.getWeed(weedId, helper.getLevel().registryAccess()).orElseThrow());
		check(crop.hasWeeds(), "the weed was not set");
		check(chunk.isUnsaved(), "spawning weeds did not mark the crop for saving, so they are lost on unload");
		chunk.setUnsaved(false);
		crop.setWeedGrowthStage(crop.getWeedGrowthStage().getNext(crop, helper.getLevel().random));
		check(chunk.isUnsaved(), "growing weeds did not mark the crop for saving");
		CompoundTag saved = crop.asBlockEntity().saveWithFullMetadata();
		BlockEntity reloaded = BlockEntity.loadStatic(absolute, helper.getLevel().getBlockState(absolute), saved);
		check(reloaded instanceof AgriCrop, "the crop block entity did not load from its tag");
		AgriCrop copy = (AgriCrop) reloaded;
		check(weedId.equals(copy.getWeedId()), "reloaded weed is '" + copy.getWeedId() + "', expected " + weedId);
		check(copy.getWeedGrowthStage() != null && copy.getWeedGrowthStage().index() == crop.getWeedGrowthStage().index(),
				"reloaded weed stage " + copy.getWeedGrowthStage() + " differs from " + crop.getWeedGrowthStage());
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
		// clicking the soil under a planted crop must not overwrite that plant
		AgriCrop occupied = matureCrop(helper, new BlockPos(1, 1, 5), "minecraft:potato", 7);
		useOn(helper, player, trowel, new BlockPos(5, 2, 5));
		check(item.hasPlant(trowel), "the trowel should pick the wheat back up");
		useOn(helper, player, trowel, new BlockPos(1, 1, 5));
		check("minecraft:potato".equals(occupied.getPlantId()) && occupied.getGenome().getGain() == 7, "the trowel overwrote the planted potato with " + occupied.getPlantId());
		check(item.hasPlant(trowel), "the trowel lost its plant on an occupied crop");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void cropSticksAddedToAPlantKeepTheirMaterial(GameTestHelper helper) {
		BlockPos soil = new BlockPos(1, 1, 1);
		helper.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
		helper.setBlock(soil.above(), ModBlocks.CROP.get().defaultBlockState().setValue(CropBlock.CROP_STATE, CropState.PLANT));
		crop(helper, soil.above()).plantGenome(genome("minecraft:wheat", 3));
		BlockPos absolute = helper.absolutePos(soil.above());
		net.minecraft.world.InteractionResult result = CropBlock.applyCropSticks(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), com.agricraft.agricraft.common.block.CropStickVariant.IRON);
		check(result == net.minecraft.world.InteractionResult.SUCCESS, "adding iron sticks to a plant returned " + result);
		check(helper.getBlockState(soil.above()).getValue(CropBlock.CROP_STATE) == CropState.PLANT_STICKS, "the plant did not get crop sticks");
		check(helper.getBlockState(soil.above()).getValue(CropBlock.STICK_VARIANT) == com.agricraft.agricraft.common.block.CropStickVariant.IRON,
				"iron sticks added to a plant became " + helper.getBlockState(soil.above()).getValue(CropBlock.STICK_VARIANT));
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void cropUpdatesWithoutWeedsOrPlantClearThem(GameTestHelper helper) {
		AgriCrop crop = cropSticks(helper, new BlockPos(1, 1, 1), CropState.SINGLE_STICKS);
		for (int tick = 0; tick < 2000 && !crop.hasWeeds(); tick++) {
			crop.applyGrowthTick();
		}
		check(crop.hasWeeds(), "empty crop sticks grew no weeds in 2000 growth ticks");
		CropBlockEntity entity = (CropBlockEntity) crop;
		// what a client receives after the rake removed the weeds
		CompoundTag update = entity.saveWithoutMetadata();
		update.putBoolean("hasWeeds", false);
		entity.load(update);
		check(!crop.hasWeeds(), "an update without weeds must clear the cached weeds");

		AgriCrop planted = matureCrop(helper, new BlockPos(3, 1, 3), "minecraft:wheat", 10);
		CropBlockEntity plantEntity = (CropBlockEntity) planted;
		CompoundTag noPlant = plantEntity.saveWithoutMetadata();
		noPlant.putBoolean("hasPlant", false);
		plantEntity.load(noPlant);
		check(!planted.hasPlant() && planted.getPlantId().isEmpty(), "an update without a plant must clear the cached plant");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void seedBagNeitherLosesNorDuplicatesSeeds(GameTestHelper helper) {
		Player player = helper.makeMockPlayer();
		ItemStack bag = new ItemStack(ModItems.SEED_BAG.get());
		ItemStack seeds = AgriSeedItem.toStack(genome("minecraft:wheat", 3));
		seeds.setCount(5);
		check(SeedBagItem.add(bag, seeds) == 5, "the bag should take five wheat seeds");

		// a slot that refuses seeds (the analyzer's journal slot) leaves them in the bag
		Slot journalOnly = new Slot(new SimpleContainer(1), 0, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModItems.JOURNAL.get());
			}
		};
		bag.getItem().overrideStackedOnOther(bag, journalOnly, ClickAction.SECONDARY, player);
		check(SeedBagItem.size(bag) == 5 && !journalOnly.hasItem(), "a slot that rejects seeds must not take them out of the bag: bag holds " + SeedBagItem.size(bag));

		// a slot that holds only one item takes one; the rest returns to the bag
		Slot single = new Slot(new SimpleContainer(1), 0, 0, 0) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}
		};
		bag.getItem().overrideStackedOnOther(bag, single, ClickAction.SECONDARY, player);
		check(single.getItem().getCount() == 1 && SeedBagItem.size(bag) == 4, "a one-item slot should take one seed and leave four in the bag: bag holds " + SeedBagItem.size(bag));

		// seeds in a slot that refuses pickup are not copied into the bag
		SimpleContainer locked = new SimpleContainer(1);
		ItemStack lockedSeeds = AgriSeedItem.toStack(genome("minecraft:wheat", 3));
		lockedSeeds.setCount(3);
		locked.setItem(0, lockedSeeds);
		Slot noPickup = new Slot(locked, 0, 0, 0) {
			@Override
			public boolean mayPickup(Player p) {
				return false;
			}
		};
		bag.getItem().overrideStackedOnOther(bag, noPickup, ClickAction.SECONDARY, player);
		check(SeedBagItem.size(bag) == 4 && locked.getItem(0).getCount() == 3, "seeds that cannot leave their slot were copied into the bag: bag holds " + SeedBagItem.size(bag));

		// an ordinary slot gives its seeds up to the bag
		Slot open = new Slot(locked, 0, 0, 0);
		bag.getItem().overrideStackedOnOther(bag, open, ClickAction.SECONDARY, player);
		check(SeedBagItem.size(bag) == 7 && locked.isEmpty(), "the bag should take three seeds from an open slot: bag holds " + SeedBagItem.size(bag));

		// a loaded trowel is not a seed
		ItemStack trowel = new ItemStack(ModItems.TROWEL.get());
		((TrowelItem) trowel.getItem()).setPlant(trowel, genome("minecraft:wheat", 3), AgriApi.getPlant(GameTestSupport.id("minecraft:wheat")).get().getInitialGrowthStage());
		check(SeedBagItem.add(bag, trowel) == 0 && trowel.getCount() == 1, "the seed bag must not swallow a loaded trowel");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void seedBagSorterChosenOnTheClientSortsTheServerBag(GameTestHelper helper) {
		Player player = helper.makeMockPlayer();
		ItemStack bag = new ItemStack(ModItems.SEED_BAG.get());
		for (int stat = 1; stat <= 3; stat++) {
			SeedBagItem.add(bag, AgriSeedItem.toStack(genome("minecraft:wheat", stat)));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, bag);
		int last = SeedBagItem.SORTERS.size() - 1;
		SeedBagItem.selectSorter(player, last);
		check(bag.getOrCreateTag().getInt("sorter") == last, "the server bag should use the sorter the client chose");
		SeedBagItem.selectSorter(player, SeedBagItem.SORTERS.size());
		SeedBagItem.selectSorter(player, -1);
		check(bag.getOrCreateTag().getInt("sorter") == last, "unknown sorter indices must be ignored");
		check(SeedBagItem.size(bag) == 3, "changing the sorter must keep every seed");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void unknownSpeciesSeedIsRejectedAndKept(GameTestHelper helper) {
		// what a seed of a disabled datapack looks like: its genome decodes, its species does not resolve
		String unknown = "agricraft:does_not_exist";
		check(AgriApi.getPlant(unknown).isEmpty(), unknown + " should not be a loaded plant");
		check(AgriApi.getPlant("Not A Valid Id!").isEmpty() && AgriApi.getWeed("Not A Valid Id!").isEmpty(), "malformed ids must resolve to nothing instead of throwing");
		AgriGenome genome = genome(unknown, 5);
		check(AgriApi.getPlantFor(genome).isEmpty(), "the unknown genome should not resolve to a plant");
		Player player = GameTestSupport.survivalPlayer(helper);
		BlockPos soil = new BlockPos(1, 1, 1);
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);

		// the seed on the crop sticks, on the soil under them, and through the crop block's own right-click
		ItemStack seed = AgriSeedItem.toStack(genome);
		seed.setCount(2);
		player.setItemInHand(InteractionHand.MAIN_HAND, seed);
		useOn(helper, player, seed, soil.above());
		useOn(helper, player, seed, soil);
		BlockPos cropPos = helper.absolutePos(soil.above());
		helper.getLevel().getBlockState(cropPos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(cropPos), Direction.UP, cropPos, false));
		// and straight through the API, which used to change the crop and then NPE in onPlanted
		crop.plantGenome(genome, player);
		check(seed.getCount() == 2, "rejected plantings consumed " + (2 - seed.getCount()) + " unknown seeds");
		check(!crop.hasPlant() && crop.getPlantId().isEmpty() && crop.getGenome() == null, "an unknown seed was planted as '" + crop.getPlantId() + "'");
		check(helper.getBlockState(soil.above()).getValue(CropBlock.CROP_STATE) == CropState.SINGLE_STICKS,
				"a rejected seed changed the crop to " + helper.getBlockState(soil.above()).getValue(CropBlock.CROP_STATE));

		// placing the seed on bare soil places no crop
		BlockPos bare = new BlockPos(4, 1, 4);
		helper.setBlock(bare, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
		useOn(helper, player, seed, bare);
		helper.assertBlockPresent(Blocks.AIR, bare.above());
		check(seed.getCount() == 2, "placing an unknown seed on soil consumed it");

		// the seed bag keeps it, on crop sticks and on bare soil
		ItemStack bag = new ItemStack(ModItems.SEED_BAG.get());
		check(SeedBagItem.add(bag, AgriSeedItem.toStack(genome)) == 1, "the bag should hold the unknown seed");
		player.setItemInHand(InteractionHand.MAIN_HAND, bag);
		useOn(helper, player, bag, soil.above());
		useOn(helper, player, bag, bare);
		helper.assertBlockPresent(Blocks.AIR, bare.above());
		check(SeedBagItem.size(bag) == 1 && !crop.hasPlant(), "the seed bag planted or lost an unknown seed");

		// a trowel carrying an unknown plant keeps it
		ItemStack trowel = new ItemStack(ModItems.TROWEL.get());
		TrowelItem item = (TrowelItem) trowel.getItem();
		item.setPlant(trowel, genome, new com.agricraft.agricraft.api.crop.AgriGrowthStage(0, 8));
		player.setItemInHand(InteractionHand.MAIN_HAND, trowel);
		useOn(helper, player, trowel, soil.above());
		useOn(helper, player, trowel, bare);
		helper.assertBlockPresent(Blocks.AIR, bare.above());
		check(item.hasPlant(trowel) && !crop.hasPlant(), "the trowel planted or lost an unknown plant");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void cropWithAnUnloadedSpeciesKeepsItsGenome(GameTestHelper helper) {
		AgriCrop crop = matureCrop(helper, new BlockPos(1, 1, 1), "minecraft:wheat", 4);
		CropBlockEntity entity = (CropBlockEntity) crop;
		// a crop saved while its datapack was enabled, loaded after it was disabled
		CompoundTag tag = entity.saveWithoutMetadata();
		genome("agricraft:does_not_exist", 4).writeToNBT(tag);
		entity.load(tag);
		check(!crop.hasPlant(), "a crop of an unloaded species should have no plant");
		for (int tick = 0; tick < 50; tick++) {
			crop.applyGrowthTick();
		}
		CompoundTag saved = entity.saveWithoutMetadata();
		AgriGenome kept = AgriGenome.fromNBT(saved);
		check(saved.getBoolean("hasPlant") && kept != null && "agricraft:does_not_exist".equals(kept.getSpeciesGene().getDominant().trait()),
				"saving a crop of an unloaded species wiped its genome");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void cropShapeFollowsTheSpecies(GameTestHelper helper) {
		BlockPos soil = new BlockPos(1, 1, 1);
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);
		CropBlockEntity entity = (CropBlockEntity) crop;
		// mature carrots are 9/16 tall, mature wheat 16/16, both at the last of eight stages; farmland lowers both by 1/16
		growTo(helper, crop, "minecraft:carrot");
		double carrot = entity.getShape().max(Direction.Axis.Y);
		check(Math.abs(carrot - 8.0 / 16.0) < 1.0E-6, "mature carrots should be 8/16 above the crop's base, got " + carrot);
		crop.removeGenome();
		growTo(helper, crop, "minecraft:wheat");
		double wheat = entity.getShape().max(Direction.Axis.Y);
		check(Math.abs(wheat - 15.0 / 16.0) < 1.0E-6, "wheat planted after carrots kept a " + wheat + " tall shape, expected 15/16");

		// a client update that swaps the species (the block entity is kept) changes the shape too
		AgriCrop other = matureCrop(helper, new BlockPos(3, 1, 3), "minecraft:carrot", 5);
		CropBlockEntity otherEntity = (CropBlockEntity) other;
		check(Math.abs(otherEntity.getShape().max(Direction.Axis.Y) - 8.0 / 16.0) < 1.0E-6, "the second crop should start as carrots");
		otherEntity.load(entity.saveWithoutMetadata());
		check(Math.abs(otherEntity.getShape().max(Direction.Axis.Y) - 15.0 / 16.0) < 1.0E-6, "loading wheat over carrots kept the carrot shape");
		helper.succeed();
	}

	private static void growTo(GameTestHelper helper, AgriCrop crop, String plant) {
		crop.plantGenome(genome(plant, 5));
		check(plant.equals(crop.getPlantId()), "could not plant " + plant);
		while (!crop.isFullyGrown()) {
			crop.setGrowthStage(crop.getGrowthStage().getNext(crop, helper.getLevel().random));
		}
	}

	@GameTest(template = TEMPLATE)
	public static void magnifyingHelmetRecipeRejectsUnrelatedItems(GameTestHelper helper) {
		Player player = helper.makeMockPlayer();
		CraftingContainer grid = new CraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
		MagnifyingHelmetRecipe recipe = new MagnifyingHelmetRecipe(GameTestSupport.id("agricraft:magnifying_helmet"));
		grid.setItem(0, new ItemStack(Items.IRON_HELMET));
		grid.setItem(1, new ItemStack(ModItems.MAGNIFYING_GLASS.get()));
		check(recipe.matches(grid, helper.getLevel()), "a helmet and a magnifying glass should match");
		check(recipe.assemble(grid).getOrCreateTag().getBoolean("magnifying"), "the result should be a magnifying helmet");
		grid.setItem(2, new ItemStack(Items.DIAMOND));
		check(!recipe.matches(grid, helper.getLevel()), "an unrelated item in the grid must stop the recipe, or crafting consumes it");
		helper.succeed();
	}

	private static void useOn(GameTestHelper helper, Player player, ItemStack stack, BlockPos relative) {
		BlockPos abs = helper.absolutePos(relative);
		stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
	}

}
