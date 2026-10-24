package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.api.genetic.AgriGenePair;
import com.agricraft.agricraft.api.genetic.AgriGeneRegistry;
import com.agricraft.agricraft.api.genetic.AgriGenome;
import com.agricraft.agricraft.api.genetic.GeneSpecies;
import com.agricraft.agricraft.api.stat.AgriStat;
import com.agricraft.agricraft.api.stat.AgriStatRegistry;
import com.agricraft.agricraft.common.block.CropBlock;
import com.agricraft.agricraft.common.block.CropState;
import com.agricraft.agricraft.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;

import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the Forge and Fabric GameTest runs. Both dev runtimes use official names. */
public final class GameTestSupport {

	public static final String TEMPLATE = "agricraft:empty";

	private GameTestSupport() {
	}

	/** A genome for {@code plant} with every stat set to {@code stat} (clamped to the stat's range). */
	static AgriGenome genome(String plant, int stat) {
		GeneSpecies species = AgriGeneRegistry.getInstance().getGeneSpecies();
		List<AgriGenePair<Integer>> stats = new ArrayList<>();
		for (AgriStat agriStat : AgriStatRegistry.getInstance()) {
			AgriGeneRegistry.getInstance().getGeneStat(agriStat).ifPresent(gene -> stats.add(new AgriGenePair<>(gene, gene.getAllele(stat))));
		}
		return new AgriGenome(new AgriGenePair<>(species, species.getAllele(plant)), stats);
	}

	/** Wet farmland at {@code soil} and crop sticks in {@code state} on top of it; returns the crop. */
	static AgriCrop cropSticks(GameTestHelper helper, BlockPos soil, CropState state) {
		helper.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
		// clear first: re-setting the same block keeps its block entity (and any plant on it)
		helper.setBlock(soil.above(), Blocks.AIR);
		helper.setBlock(soil.above(), ModBlocks.CROP.get().defaultBlockState().setValue(CropBlock.CROP_STATE, state));
		return crop(helper, soil.above());
	}

	static AgriCrop crop(GameTestHelper helper, BlockPos relative) {
		return AgriApi.getCrop(helper.getLevel(), helper.absolutePos(relative))
				.orElseThrow(() -> new GameTestAssertException("no AgriCraft crop at " + relative));
	}

	/** Plants {@code plant} on fresh crop sticks and grows it to its final stage. */
	static AgriCrop matureCrop(GameTestHelper helper, BlockPos soil, String plant, int stat) {
		AgriCrop crop = cropSticks(helper, soil, CropState.SINGLE_STICKS);
		crop.plantGenome(genome(plant, stat));
		while (!crop.isFullyGrown()) {
			crop.setGrowthStage(crop.getGrowthStage().getNext(crop, helper.getLevel().random));
		}
		return crop;
	}

	/** Like {@link GameTestHelper#makeMockPlayer()}, but in survival, so item use really consumes items. */
	static net.minecraft.world.entity.player.Player survivalPlayer(GameTestHelper helper) {
		return new net.minecraft.world.entity.player.Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "agricraft-survival-player"), null) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return false;
			}
		};
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(message);
		}
	}

	static ResourceLocation id(String id) {
		return new ResourceLocation(id);
	}

}
