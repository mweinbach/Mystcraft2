package com.agricraft.agricraft.compat.botania;

import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.api.plant.IAgriPlantModifier;
import com.agricraft.agricraft.api.requirement.AgriGrowthConditionRegistry;
import com.agricraft.agricraft.api.requirement.AgriGrowthResponse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ManaGrowthCondition extends AgriGrowthConditionRegistry.BaseGrowthCondition<Integer> {

	/**
	 * The closest pool of each crop, per level. 4.0.6 keyed it by position alone, so crops at the same coordinates in
	 * another dimension, or on the integrated server's client, were fed by (and drained) a pool of another level, and
	 * a pool that was gone or newly placed nearer was never looked up again.
	 */
	private static final Map<Level, Map<BlockPos, WeakReference<ManaPool>>> POOL_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	public ManaGrowthCondition() {
		// set null for response and getter 'cause we are doing it ourselves
		super("botania_mana", null, null);
	}

	public static ManaPool getPool(BlockPos pos, Level level) {
		Map<BlockPos, WeakReference<ManaPool>> pools = POOL_CACHE.computeIfAbsent(level, key -> new ConcurrentHashMap<>());
		WeakReference<ManaPool> ref = pools.get(pos);
		ManaPool pool = ref == null ? null : ref.get();
		if (!isUsable(pool, level)) {
			pool = BotaniaAPI.instance().getManaNetworkInstance().getClosestPool(pos, level, 16);
			pools.put(pos.immutable(), new WeakReference<>(pool));
		}
		return pool;
	}

	/** A cached pool is only used while it still exists, in the crop's own level. */
	private static boolean isUsable(@Nullable ManaPool pool, Level level) {
		if (pool == null || pool.getManaReceiverLevel() != level) {
			return false;
		}
		return !(pool instanceof BlockEntity blockEntity) || !blockEntity.isRemoved();
	}

	/** A pool left the mana network: forget it wherever it was cached. */
	public static void removePoll(ManaReceiver pool) {
		synchronized (POOL_CACHE) {
			POOL_CACHE.values().forEach(pools -> pools.values().removeIf(ref -> ref.get() == pool));
		}
	}

	/** A pool joined the mana network: it may be closer than the cached ones of its level, so look them up again. */
	public static void poolAdded(ManaReceiver pool) {
		Level level = pool.getManaReceiverLevel();
		if (level != null) {
			POOL_CACHE.remove(level);
		}
	}

	@Override
	public AgriGrowthResponse check(AgriCrop crop, Level level, BlockPos pos, int strength) {
		if (crop.getPlant() == null) {
			return AgriGrowthResponse.FERTILE;
		}
		// kinda tricky, but we check if the plant has the mana modifier
		Optional<IAgriPlantModifier> manaModifier = crop.getPlant().getModifiers().filter(m -> m.getClass() == ManaPlantModifier.class).findFirst();
		if (manaModifier.isPresent()) {
			ManaPlantModifier modifier = (ManaPlantModifier) manaModifier.get();
			ManaPool pool = getPool(pos, level);
			if (pool != null && pool.isOutputtingPower()) {
				if (pool.getCurrentMana() >= modifier.cost()) {
					return AgriGrowthResponse.FERTILE;
				}
			}
			return AgriGrowthResponse.INFERTILE;
		}
		return AgriGrowthResponse.FERTILE;
	}

	@Override
	public AgriGrowthResponse apply(AgriPlant plant, int strength, Integer value) {
		// we're not displaying the condition on the journal nor jei nor emi, so we can return any value we want.
		return AgriGrowthResponse.FERTILE;
	}

}
