package com.agricraft.agricraft.compat;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.api.requirement.AgriGrowthConditionRegistry;
import com.agricraft.agricraft.api.requirement.AgriGrowthResponse;
import com.agricraft.agricraft.api.codecs.AgriSoil;
import com.agricraft.agricraft.api.stat.AgriStatRegistry;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.util.LangUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The lines AgriCraft adds to the Jade and WTHIT tooltips. Upstream's two plugins carried identical copies of the crop
 * and soil logic; this port keeps one copy here so both plugins (and the GameTests) share it. The irrigation lines are
 * AgriCraft 3's {@code AgriWailaIrrigationBlockInfoProvider}.
 */
public final class AgriTooltipLines {

	private AgriTooltipLines() {
	}

	/** Upstream's crop provider: growth, and with sneak the species, stats and unmet growth conditions; weeds. */
	public static void crop(AgriCrop crop, boolean sneaking, Consumer<Component> out) {
		if (crop.hasPlant()) {
			out.accept(Component.translatable("agricraft.tooltip.jade.growth", (int) (crop.getGrowthPercent() * 100)));
			if (sneaking) {
				out.accept(Component.translatable("agricraft.tooltip.jade.species")
						.append(LangUtils.plantName(crop.getGenome().getSpeciesGene().getTrait())));
				AgriStatRegistry.getInstance().stream()
						.filter(stat -> !stat.isHidden())
						.map(stat -> crop.getGenome().getStatGene(stat))
						.sorted(Comparator.comparing(pair -> pair.getGene().getId()))
						.map(pair -> Component.translatable("agricraft.tooltip.jade.stat." + pair.getGene().getId(), pair.getTrait()))
						.forEach(out);
				Level level = crop.getLevel();
				if (level != null && level.isClientSide()) {
					// the client level does not update its sky brightness every tick
					level.updateSkyBrightness();
				}
				AgriGrowthResponse response = crop.getFertilityResponse();
				out.accept(Component.translatable("agricraft.tooltip.magnifying.requirement." + (response.isLethal() ? "lethal" : response.isFertile() ? "fertile" : "not_fertile")));
				if (!response.isFertile()) {
					int strength = crop.getGenome().getStrength();
					AgriGrowthConditionRegistry.getInstance().stream()
							.filter(condition -> !condition.check(crop, level, crop.getBlockPos(), strength).isFertile())
							.forEach(condition -> condition.notMetDescription(out));
				}
			}
		} else {
			out.accept(Component.translatable("agricraft.tooltip.magnifying.no_plant"));
		}
		if (crop.hasWeeds()) {
			out.accept(Component.translatable("agricraft.tooltip.magnifying.weeds").append(LangUtils.weedName(crop.getWeedId())));
			if (sneaking) {
				out.accept(Component.literal("  ").append(Component.translatable("agricraft.tooltip.magnifying.growth",
						crop.getWeedGrowthStage().index() + 1, crop.getWeedGrowthStage().total())));
			}
		}
	}

	/** Upstream's soil provider: with sneak, the humidity, acidity and nutrients of an AgriCraft soil. */
	public static void soil(Level level, BlockPos pos, boolean sneaking, Consumer<Component> out) {
		if (!sneaking) {
			return;
		}
		Optional<AgriSoil> found = AgriApi.getSoil(level, pos, level.registryAccess());
		found.ifPresent(soil -> {
			out.accept(Component.translatable("agricraft.tooltip.magnifying.soil.humidity")
					.append(Component.translatable("agricraft.soil.humidity." + soil.humidity().name().toLowerCase(Locale.ROOT))));
			out.accept(Component.translatable("agricraft.tooltip.magnifying.soil.acidity")
					.append(Component.translatable("agricraft.soil.acidity." + soil.acidity().name().toLowerCase(Locale.ROOT))));
			out.accept(Component.translatable("agricraft.tooltip.magnifying.soil.nutrients")
					.append(Component.translatable("agricraft.soil.nutrients." + soil.nutrients().name().toLowerCase(Locale.ROOT))));
		});
	}

	/** AgriCraft 3's irrigation provider: the valve state of a channel, then the water held. */
	public static void irrigation(IrrigationComponentBlockEntity component, Consumer<Component> out) {
		BlockState state = component.getBlockState();
		if (state.hasProperty(IrrigationChannelBlock.VALVE)) {
			IrrigationChannelBlock.Valve valve = state.getValue(IrrigationChannelBlock.VALVE);
			if (valve != IrrigationChannelBlock.Valve.NONE) {
				out.accept(Component.translatable(valve == IrrigationChannelBlock.Valve.OPEN ? "agricraft.tooltip.valve.open" : "agricraft.tooltip.valve.closed"));
			}
		}
		out.accept(Component.literal(component.getContent() + " / " + component.getCapacity() + " mB"));
	}

}
