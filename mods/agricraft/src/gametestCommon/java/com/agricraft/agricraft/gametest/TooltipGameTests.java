package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.common.block.CropState;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationChannelBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.compat.AgriTooltipLines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;
import static com.agricraft.agricraft.gametest.GameTestSupport.cropSticks;
import static com.agricraft.agricraft.gametest.GameTestSupport.matureCrop;

/** The Jade and WTHIT tooltip lines, which both plugins take from {@link AgriTooltipLines}. */
public class TooltipGameTests {

	private static String key(Component line) {
		return line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : line.getString();
	}

	private static List<String> keys(List<Component> lines) {
		return lines.stream().map(TooltipGameTests::key).toList();
	}

	@GameTest(template = TEMPLATE)
	public static void cropLinesShowGrowthAndStatsWhenSneaking(GameTestHelper helper) {
		AgriCrop crop = matureCrop(helper, new BlockPos(1, 1, 1), "minecraft:wheat", 4);
		List<Component> plain = new ArrayList<>();
		AgriTooltipLines.crop(crop, false, plain::add);
		check(keys(plain).equals(List.of("agricraft.tooltip.jade.growth")), "without sneaking only growth should show, got " + keys(plain));
		check(plain.get(0).getString().contains("100"), "a mature crop should show 100% growth, got " + plain.get(0).getString());
		List<Component> sneaking = new ArrayList<>();
		AgriTooltipLines.crop(crop, true, sneaking::add);
		List<String> keys = keys(sneaking);
		check(keys.contains("agricraft.tooltip.jade.species"), "sneaking should show the species, got " + keys);
		for (String stat : List.of("gain", "growth", "strength", "resistance", "fertility", "mutativity")) {
			check(keys.contains("agricraft.tooltip.jade.stat." + stat), "sneaking should show the " + stat + " stat, got " + keys);
		}
		check(keys.stream().anyMatch(k -> k.startsWith("agricraft.tooltip.magnifying.requirement.")), "sneaking should show the fertility verdict");

		AgriCrop empty = cropSticks(helper, new BlockPos(3, 1, 3), CropState.SINGLE_STICKS);
		List<Component> none = new ArrayList<>();
		AgriTooltipLines.crop(empty, true, none::add);
		check(keys(none).equals(List.of("agricraft.tooltip.magnifying.no_plant")), "empty crop sticks should say so, got " + keys(none));
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void soilLinesNeedSneakingAndASoil(GameTestHelper helper) {
		BlockPos farmland = new BlockPos(1, 1, 1);
		BlockPos stone = new BlockPos(3, 1, 3);
		helper.setBlock(farmland, Blocks.FARMLAND);
		helper.setBlock(stone, Blocks.STONE);
		List<Component> lines = new ArrayList<>();
		AgriTooltipLines.soil(helper.getLevel(), helper.absolutePos(farmland), false, lines::add);
		check(lines.isEmpty(), "soil properties should only show while sneaking");
		AgriTooltipLines.soil(helper.getLevel(), helper.absolutePos(farmland), true, lines::add);
		check(keys(lines).equals(List.of("agricraft.tooltip.magnifying.soil.humidity", "agricraft.tooltip.magnifying.soil.acidity",
				"agricraft.tooltip.magnifying.soil.nutrients")), "farmland should list its humidity, acidity and nutrients, got " + keys(lines));
		lines.clear();
		AgriTooltipLines.soil(helper.getLevel(), helper.absolutePos(stone), true, lines::add);
		check(lines.isEmpty(), "stone is not an AgriCraft soil");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void irrigationLinesShowWaterAndValve(GameTestHelper helper) {
		IrrigationTankBlockEntity tank = IrrigationGameTests.tank(helper, new BlockPos(1, 1, 1));
		tank.pushWater(1000, true);
		List<Component> lines = new ArrayList<>();
		AgriTooltipLines.irrigation(tank, lines::add);
		check(lines.size() == 1 && lines.get(0).getString().equals("1000 / " + IrrigationConfig.tankCapacity + " mB"),
				"a tank should show its water, got " + lines.stream().map(Component::getString).toList());
		IrrigationChannelBlockEntity valve = IrrigationGameTests.channel(helper, new BlockPos(4, 1, 4), IrrigationChannelBlock.Valve.CLOSED);
		lines.clear();
		AgriTooltipLines.irrigation(valve, lines::add);
		check(keys(lines).get(0).equals("agricraft.tooltip.valve.closed") && lines.size() == 2,
				"a closed valve should say so before the water line, got " + keys(lines));
		helper.succeed();
	}

}
