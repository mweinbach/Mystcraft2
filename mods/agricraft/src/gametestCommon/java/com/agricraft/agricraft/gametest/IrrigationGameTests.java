package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationChannelBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.block.irrigation.IrrigationTankBlock;
import com.agricraft.agricraft.common.block.irrigation.SprinklerBlock;
import com.agricraft.agricraft.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;

/** Irrigation (ported from AgriCraft 3): tanks, channels, valves and sprinklers. */
public class IrrigationGameTests {

	static <T extends IrrigationComponentBlockEntity> T component(GameTestHelper helper, BlockPos pos, Class<T> type) {
		if (type.isInstance(helper.getBlockEntity(pos))) {
			return type.cast(helper.getBlockEntity(pos));
		}
		throw new GameTestAssertException("no " + type.getSimpleName() + " at " + pos);
	}

	/** Places a block with the state it would have if it had been placed next to its current neighbours. */
	static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
		helper.setBlock(pos, state);
		BlockPos absolute = helper.absolutePos(pos);
		helper.getLevel().setBlock(absolute, Block.updateFromNeighbourShapes(state, helper.getLevel(), absolute), Block.UPDATE_ALL);
	}

	static IrrigationTankBlockEntity tank(GameTestHelper helper, BlockPos pos) {
		place(helper, pos, ModBlocks.IRRIGATION_TANK.get().defaultBlockState());
		IrrigationTankBlockEntity tank = component(helper, pos, IrrigationTankBlockEntity.class);
		tank.checkAndFormMultiBlock();
		return tank;
	}

	static IrrigationChannelBlockEntity channel(GameTestHelper helper, BlockPos pos, IrrigationChannelBlock.Valve valve) {
		place(helper, pos, ModBlocks.IRRIGATION_CHANNEL.get().defaultBlockState().setValue(IrrigationChannelBlock.VALVE, valve));
		return component(helper, pos, IrrigationChannelBlockEntity.class);
	}

	private static void use(GameTestHelper helper, Player player, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		BlockState state = helper.getLevel().getBlockState(absolute);
		state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
	}

	@GameTest(template = TEMPLATE)
	public static void tankFillsFromBucketsAndRain(GameTestHelper helper) {
		IrrigationTankBlockEntity tank = tank(helper, new BlockPos(1, 1, 1));
		check(tank.getContent() == 0, "a new tank should be empty");
		Player player = helper.makeMockPlayer();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
		use(helper, player, new BlockPos(1, 1, 1));
		check(tank.getContent() == 1000, "a water bucket should add 1000 mB, got " + tank.getContent());
		check(tank.getFluidLevel() > tank.getMinLevel(), "the water level should rise above the tank floor");
		// rain adds the configured rate
		BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().getBlockState(absolute).getBlock().handlePrecipitation(helper.getLevel().getBlockState(absolute), helper.getLevel(), absolute, Biome.Precipitation.RAIN);
		check(tank.getContent() == 1005, "rain should add 5 mB, got " + tank.getContent());
		// an empty bucket takes a full bucket back out
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		use(helper, player, new BlockPos(1, 1, 1));
		check(tank.getContent() == 5, "an empty bucket should drain 1000 mB, got " + tank.getContent());
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void tanksMergeIntoAMultiBlockAndSplitAgain(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 1, 1);
		BlockPos b = new BlockPos(2, 1, 1);
		IrrigationTankBlockEntity first = tank(helper, a);
		first.pushWater(4000, true);
		IrrigationTankBlockEntity second = tank(helper, b);
		check(first.isMultiBlock() && second.isMultiBlock(), "two touching tanks should form one multi-block");
		check(first.isMultiBlockOrigin() && !second.isMultiBlockOrigin(), "the minimum corner should hold the water");
		check(second.getCapacity() == 2 * IrrigationConfig.tankCapacity, "the multi-block capacity should double, got " + second.getCapacity());
		check(second.getContent() == 4000, "the water should be pooled, got " + second.getContent());
		check(helper.getBlockState(a).getValue(IrrigationTankBlock.EAST) == IrrigationTankBlock.Connection.TANK, "the shared wall should be open");
		// removing one block splits the structure and keeps the water level
		float level = first.getFluidLevel();
		helper.setBlock(b, Blocks.AIR);
		check(!first.isMultiBlock(), "removing a block should split the multi-block");
		check(Math.abs(first.getFluidLevel() - level) < 0.01F, "the remaining tank should keep the water level");
		check(Math.abs(first.getContent() - 2000) <= 1, "half of the pooled water should remain, got " + first.getContent());
		check(helper.getBlockState(a).getValue(IrrigationTankBlock.EAST) == IrrigationTankBlock.Connection.NONE, "the wall should close again");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void stackedTanksSplitWithoutMakingOrLosingWater(GameTestHelper helper) {
		BlockPos bottom = new BlockPos(1, 1, 1);
		BlockPos top = new BlockPos(1, 2, 1);
		IrrigationTankBlockEntity lower = tank(helper, bottom);
		lower.pushWater(4000, true);
		// adding and removing an empty tank on top must leave exactly the water there was
		for (int round = 0; round < 3; round++) {
			IrrigationTankBlockEntity upper = tank(helper, top);
			check(upper.isMultiBlock() && upper.getContent() == 4000, "round " + round + ": the stacked tanks should pool 4000 mB, got " + upper.getContent());
			helper.setBlock(top, Blocks.AIR);
			check(!lower.isMultiBlock() && lower.getContent() == 4000, "round " + round + ": removing the empty top tank left " + lower.getContent() + " mB instead of 4000");
		}
		// water fills from the bottom: with 12000 mB the bottom tank is full and the top one holds the rest
		IrrigationTankBlockEntity upper = tank(helper, top);
		upper.pushWater(8000, true);
		check(upper.getContent() == 12000, "the stacked tanks should pool 12000 mB, got " + upper.getContent());
		helper.setBlock(top, Blocks.AIR);
		check(lower.getContent() == IrrigationConfig.tankCapacity, "the bottom tank should keep a full tank, got " + lower.getContent());
		// and the top tank keeps its own part when the bottom one is removed
		upper = tank(helper, top);
		IrrigationTankBlockEntity origin = component(helper, bottom, IrrigationTankBlockEntity.class);
		origin.setContent(12000);
		helper.setBlock(bottom, Blocks.AIR);
		check(!upper.isMultiBlock() && upper.getContent() == 12000 - IrrigationConfig.tankCapacity,
				"the top tank should keep the water above the removed bottom tank, got " + upper.getContent());
		helper.succeed();
	}

	@GameTest(template = TEMPLATE, timeoutTicks = 200)
	public static void channelsSpreadWaterFromATank(GameTestHelper helper) {
		// tanks, channels and sprinklers work in their tickers; tick them even before the chunk does
		GameTestTicking.keepTestAreaTicking(helper);
		IrrigationTankBlockEntity tank = tank(helper, new BlockPos(1, 1, 1));
		tank.pushWater(8000, true);
		IrrigationChannelBlockEntity first = channel(helper, new BlockPos(2, 1, 1), IrrigationChannelBlock.Valve.NONE);
		IrrigationChannelBlockEntity second = channel(helper, new BlockPos(3, 1, 1), IrrigationChannelBlock.Valve.NONE);
		IrrigationChannelBlockEntity last = channel(helper, new BlockPos(4, 1, 1), IrrigationChannelBlock.Valve.NONE);
		check(helper.getBlockState(new BlockPos(2, 1, 1)).getValue(IrrigationChannelBlock.WEST), "a channel should connect to the tank");
		check(helper.getBlockState(new BlockPos(1, 1, 1)).getValue(IrrigationTankBlock.EAST) == IrrigationTankBlock.Connection.CHANNEL, "the tank should connect to the channel");
		helper.succeedWhen(() -> {
			int total = tank.getContent() + first.getContent() + second.getContent() + last.getContent();
			check(total == 8000, "water must be conserved while spreading, total " + total);
			check(last.getContent() > 0, "water should reach the last channel");
			check(tank.getContent() < 8000, "the tank should drain into the channels");
		});
	}

	@GameTest(template = TEMPLATE, timeoutTicks = 200)
	public static void closedValveBlocksTheFlow(GameTestHelper helper) {
		// tanks, channels and sprinklers work in their tickers; tick them even before the chunk does
		GameTestTicking.keepTestAreaTicking(helper);
		IrrigationTankBlockEntity tank = tank(helper, new BlockPos(1, 1, 1));
		tank.pushWater(8000, true);
		BlockPos valvePos = new BlockPos(2, 1, 1);
		IrrigationChannelBlockEntity valve = channel(helper, valvePos, IrrigationChannelBlock.Valve.CLOSED);
		IrrigationChannelBlockEntity behind = channel(helper, new BlockPos(3, 1, 1), IrrigationChannelBlock.Valve.NONE);
		helper.startSequence()
				.thenExecuteAfter(40, () -> {
					check(valve.getContent() == 0 && behind.getContent() == 0, "a closed valve should hold the water back");
					check(tank.getContent() == 8000, "the tank should not lose water through a closed valve");
					// opening the valve by hand lets the water through
					use(helper, helper.makeMockPlayer(), valvePos);
					check(helper.getBlockState(valvePos).getValue(IrrigationChannelBlock.VALVE) == IrrigationChannelBlock.Valve.OPEN, "using the valve should open it");
				})
				.thenWaitUntil(() -> check(behind.getContent() > 0, "water should pass the opened valve"))
				.thenSucceed();
	}

	@GameTest(template = TEMPLATE, timeoutTicks = 200)
	public static void sprinklerDrainsTheChannelAndMoistensFarmland(GameTestHelper helper) {
		// tanks, channels and sprinklers work in their tickers; tick them even before the chunk does
		GameTestTicking.keepTestAreaTicking(helper);
		IrrigationChannelBlockEntity channel = channel(helper, new BlockPos(3, 4, 3), IrrigationChannelBlock.Valve.NONE);
		channel.pushWater(500, true);
		BlockPos sprinklerPos = new BlockPos(3, 3, 3);
		helper.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
		BlockPos farmland = new BlockPos(2, 1, 2);
		helper.setBlock(farmland, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
		helper.succeedWhen(() -> {
			check(helper.getBlockState(sprinklerPos).getValue(SprinklerBlock.ACTIVE), "a sprinkler with water should be active");
			check(channel.getContent() < 500, "the sprinkler should pull water from the channel");
			check(helper.getBlockState(farmland).getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE, "farmland in range should be moistened");
		});
	}

	@GameTest(template = TEMPLATE)
	public static void sprinklerNeedsAChannelAbove(GameTestHelper helper) {
		BlockPos channelPos = new BlockPos(3, 4, 3);
		BlockPos sprinklerPos = new BlockPos(3, 3, 3);
		channel(helper, channelPos, IrrigationChannelBlock.Valve.NONE);
		helper.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
		helper.setBlock(channelPos, Blocks.AIR);
		helper.assertBlockNotPresent(ModBlocks.SPRINKLER.get(), sprinklerPos);
		helper.succeed();
	}

}
