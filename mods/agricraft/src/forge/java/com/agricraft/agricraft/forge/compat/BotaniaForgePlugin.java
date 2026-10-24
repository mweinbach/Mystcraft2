package com.agricraft.agricraft.forge.compat;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import com.agricraft.agricraft.compat.botania.AgriHornHarvestable;
import com.agricraft.agricraft.compat.botania.BotaniaPlugin;
import com.agricraft.agricraft.compat.botania.ManaGrowthCondition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaBlockType;
import vazkii.botania.api.mana.ManaNetworkAction;
import vazkii.botania.api.mana.ManaNetworkEvent;

/**
 * Upstream's Forge Botania hook: the mana growth condition and plant modifier, the pool cache cleanup that upstream
 * registered in {@code AgriCraftForge}, and the Horn of the Wild harvest that upstream's {@code ForgeCropBlockEntity}
 * exposed as a capability (attached here, since this port's crop block entity is shared). Called only when Botania is
 * loaded and the {@code compat.botania} option is on.
 */
public final class BotaniaForgePlugin {

	private static final ResourceLocation HORN_ID = new ResourceLocation(AgriApi.MOD_ID, "horn_harvest");

	private BotaniaForgePlugin() {
	}

	public static void init() {
		BotaniaPlugin.init();
		MinecraftForge.EVENT_BUS.addListener((ManaNetworkEvent event) -> {
			if (event.getAction() == ManaNetworkAction.REMOVE && event.getType() == ManaBlockType.POOL) {
				ManaGrowthCondition.removePoll(event.getReceiver());
			}
		});
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, BotaniaForgePlugin::attach);
	}

	private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
		if (!(event.getObject() instanceof CropBlockEntity)) {
			return;
		}
		LazyOptional<AgriHornHarvestable> horn = LazyOptional.of(() -> AgriHornHarvestable.INSTANCE);
		event.addCapability(HORN_ID, new ICapabilityProvider() {
			@Override
			public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
				return cap == BotaniaForgeCapabilities.HORN_HARVEST ? horn.cast() : LazyOptional.empty();
			}
		});
	}

}
