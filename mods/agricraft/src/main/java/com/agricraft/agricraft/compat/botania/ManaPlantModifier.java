package com.agricraft.agricraft.compat.botania;

import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.api.plant.IAgriPlantModifier;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.mana.ManaPool;

public record ManaPlantModifier(int cost) implements IAgriPlantModifier {

	public static final String ID = "agricraft:botania_mana";

	@Override
	public void onGrowth(AgriCrop crop) {
		ManaPool pool = ManaGrowthCondition.getPool(crop.getBlockPos(), crop.getLevel());
		// upstream dereferenced the pool unguarded; growth only runs when the condition found one, but the cache can drop it
		if (pool != null && pool.isOutputtingPower()) {
			pool.receiveMana(-cost);
		}
		BotaniaAPI.instance().sparkleFX(crop.getLevel(),
				crop.getBlockPos().getX() + 0.5 + 0.5 * Math.random(),
				crop.getBlockPos().getY() + 0.5 + 0.5 * Math.random(),
				crop.getBlockPos().getZ() + 0.5 + 0.5 * Math.random(),
				67.0F / 255.0F,
				180.0F / 255.0F,
				1.0F,
				(float) Math.random(),
				5
		);
	}

}
