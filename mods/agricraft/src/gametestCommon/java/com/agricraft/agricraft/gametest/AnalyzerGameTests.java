package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.common.block.SeedAnalyzerBlock;
import com.agricraft.agricraft.common.block.entity.SeedAnalyzerBlockEntity;
import com.agricraft.agricraft.common.inventory.container.SeedAnalyzerMenu;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.item.JournalItem;
import com.agricraft.agricraft.common.registry.ModBlocks;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;
import static com.agricraft.agricraft.gametest.GameTestSupport.genome;

/** The seed analyzer: seed and journal slots, journal research and automation faces. */
public class AnalyzerGameTests {

	private static SeedAnalyzerBlockEntity analyzer(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.SEED_ANALYZER.get());
		if (helper.getBlockEntity(pos) instanceof SeedAnalyzerBlockEntity analyzer) {
			return analyzer;
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("no seed analyzer block entity at " + pos);
	}

	@GameTest(template = TEMPLATE)
	public static void analyzerResearchesSeedsIntoTheJournal(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		SeedAnalyzerBlockEntity analyzer = analyzer(helper, pos);
		ItemStack journal = new ItemStack(ModItems.JOURNAL.get());
		check(analyzer.insertJournal(journal).isEmpty(), "the journal was not accepted");
		check(helper.getBlockState(pos).getValue(SeedAnalyzerBlock.JOURNAL), "the analyzer does not show the inserted journal");
		check(JournalItem.getResearchedPlants(analyzer.getJournal()) == 0, "a new journal should have no researched plants");

		ItemStack seed = AgriSeedItem.toStack(genome("minecraft:wheat", 3));
		Player player = helper.makeMockPlayer();
		player.setItemInHand(InteractionHand.MAIN_HAND, seed);
		BlockPos abs = helper.absolutePos(pos);
		seed.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
		check(seed.isEmpty(), "using a seed on the analyzer should move it into the analyzer");
		check(analyzer.hasSeed() && "minecraft:wheat".equals(AgriSeedItem.getSpecies(analyzer.getSeed())), "the analyzer does not hold the wheat seed");
		check(JournalItem.getResearchedPlants(analyzer.getJournal()) == 1, "analyzing wheat with a journal inside did not research it");

		ItemStack second = AgriSeedItem.toStack(genome("minecraft:potato", 3));
		player.setItemInHand(InteractionHand.MAIN_HAND, second);
		second.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
		check(second.getCount() == 1, "an occupied analyzer must not take a second seed");

		ItemStack extracted = analyzer.extractSeed();
		check("minecraft:wheat".equals(AgriSeedItem.getSpecies(extracted)), "extracting returned " + extracted);
		check(!analyzer.hasSeed(), "the seed slot is not empty after extraction");
		ItemStack journalOut = analyzer.extractJournal();
		check(JournalItem.getResearchedPlants(journalOut) == 1, "the extracted journal lost its research");
		check(!helper.getBlockState(pos).getValue(SeedAnalyzerBlock.JOURNAL), "the analyzer still shows a journal");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void analyzerExposesOnlyTheJournalSlotToAutomation(GameTestHelper helper) {
		SeedAnalyzerBlockEntity analyzer = analyzer(helper, new BlockPos(1, 1, 1));
		for (Direction side : Direction.values()) {
			int[] slots = analyzer.getSlotsForFace(side);
			check(slots.length == 1 && slots[0] == SeedAnalyzerBlockEntity.JOURNAL_SLOT, "automation from " + side + " should only reach the journal slot");
		}
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void analyzerSlotEditsFromTheMenuAreSaved(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		SeedAnalyzerBlockEntity analyzer = analyzer(helper, pos);
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(pos));
		chunk.setUnsaved(false);
		// the analyzer menu's slots write straight into the container, bypassing insertSeed()
		new Slot(analyzer.getInventory(), SeedAnalyzerBlockEntity.SEED_SLOT, 0, 0).set(AgriSeedItem.toStack(genome("minecraft:wheat", 3)));
		check(chunk.isUnsaved(), "a seed placed through the menu should mark the analyzer's chunk for saving");
		chunk.setUnsaved(false);
		new Slot(analyzer.getInventory(), SeedAnalyzerBlockEntity.SEED_SLOT, 0, 0).remove(1);
		check(chunk.isUnsaved(), "a seed taken through the menu should mark the analyzer's chunk for saving");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void analyzerMenuClosesWhenTheAnalyzerIsBroken(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		analyzer(helper, pos);
		Player player = helper.makeMockPlayer();
		BlockPos abs = helper.absolutePos(pos);
		player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
		SeedAnalyzerMenu menu = new SeedAnalyzerMenu(0, player.getInventory(), player, abs);
		check(menu.stillValid(player), "the menu of a standing analyzer next to the player should be valid");
		helper.setBlock(pos, Blocks.AIR);
		check(!menu.stillValid(player), "the menu must close once its analyzer is broken, or items go into a detached inventory");
		helper.setBlock(pos, ModBlocks.SEED_ANALYZER.get());
		check(!menu.stillValid(player), "a new analyzer at the same spot is not the menu's analyzer");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void analyzerAcceptsOnlyAJournalFromAutomation(GameTestHelper helper) {
		SeedAnalyzerBlockEntity analyzer = analyzer(helper, new BlockPos(1, 1, 1));
		int slot = SeedAnalyzerBlockEntity.JOURNAL_SLOT;
		check(!analyzer.canPlaceItemThroughFace(slot, new ItemStack(Items.DIRT), Direction.UP), "a hopper must not push dirt into the journal slot");
		check(!analyzer.canPlaceItemThroughFace(slot, AgriSeedItem.toStack(genome("minecraft:wheat", 3)), Direction.UP), "a hopper must not push seeds into the journal slot");
		check(analyzer.canPlaceItemThroughFace(slot, new ItemStack(ModItems.JOURNAL.get()), Direction.UP), "a hopper should insert a journal");
		analyzer.insertJournal(new ItemStack(ModItems.JOURNAL.get()));
		check(!analyzer.canPlaceItemThroughFace(slot, new ItemStack(ModItems.JOURNAL.get()), Direction.UP), "the analyzer holds one journal");
		helper.succeed();
	}

}
