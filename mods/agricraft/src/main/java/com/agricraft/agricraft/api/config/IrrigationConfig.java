package com.agricraft.agricraft.api.config;

import com.teamresourceful.resourcefulconfig.common.annotations.Category;
import com.teamresourceful.resourcefulconfig.common.annotations.Comment;
import com.teamresourceful.resourcefulconfig.common.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.common.annotations.DoubleRange;
import com.teamresourceful.resourcefulconfig.common.annotations.IntRange;
import com.teamresourceful.resourcefulconfig.common.config.EntryType;

/**
 * Irrigation settings. Irrigation is not part of AgriCraft 4.0.6; the tank, channels, valve and sprinkler are ported
 * from AgriCraft 3 (1.16.5) and keep its option names and defaults.
 */
@Category(id = "irrigation", translation = "config.agricraft.irrigation")
public final class IrrigationConfig {

	@ConfigEntry(id = "tank_capacity", type = EntryType.INTEGER, translation = "config.agricraft.irrigation.tank_capacity")
	@IntRange(min = 1000, max = 40000)
	@Comment("Configures the capacity (in mB) of one tank block")
	public static int tankCapacity = 8000;

	@ConfigEntry(id = "channel_capacity", type = EntryType.INTEGER, translation = "config.agricraft.irrigation.channel_capacity")
	@IntRange(min = 50, max = 2000)
	@Comment("Configures the capacity (in mB) of one channel block")
	public static int channelCapacity = 500;

	@ConfigEntry(id = "rain_fill_rate", type = EntryType.INTEGER, translation = "config.agricraft.irrigation.rain_fill_rate")
	@IntRange(min = 0, max = 50)
	@Comment("Configures the amount of water (in mB) a tank gains each time it is rained on (0 disables filling from rainfall)")
	public static int rainFillRate = 5;

	@ConfigEntry(id = "tank_spawn_water_block", type = EntryType.BOOLEAN, translation = "config.agricraft.irrigation.tank_spawn_water_block")
	@Comment("Set to false to disable water tanks spawning a water block when broken when sufficiently full")
	public static boolean tankSpawnWaterBlock = true;

	@ConfigEntry(id = "sprinkle_interval", type = EntryType.INTEGER, translation = "config.agricraft.irrigation.sprinkle_interval")
	@IntRange(min = 1, max = 1200)
	@Comment("The minimum number of ticks between successive starts of irrigation.")
	public static int sprinkleInterval = 40;

	@ConfigEntry(id = "sprinkle_growth_chance", type = EntryType.DOUBLE, translation = "config.agricraft.irrigation.sprinkle_growth_chance")
	@DoubleRange(min = 0.0, max = 1.0)
	@Comment("Every loop, each unobscured plant in sprinkler range has this chance to get a growth tick from the sprinkler.")
	public static double sprinkleGrowthChance = 0.2;

	@ConfigEntry(id = "sprinkler_water_consumption", type = EntryType.INTEGER, translation = "config.agricraft.irrigation.sprinkler_water_consumption")
	@IntRange(min = 0, max = 1000)
	@Comment("Defined in terms of mB per second. The irrigation loop progress will pause when there is insufficient water.")
	public static int sprinklerWaterConsumption = 10;

}
