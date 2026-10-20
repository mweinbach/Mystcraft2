package it.zerono.mods.extremereactors.gamecontent.fluid;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.gamecontent.CommonConstants;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.Reactants;
import net.minecraft.world.item.Rarity;
import ports.extremereactors.platform.FluidDefinition;

public final class ReactorFluidType {

    public static FluidDefinition of(final String registrationName, final int tint, final int density, final int lightLevel, final Rarity rarity) {
        return new FluidDefinition(registrationName, "fluid." + ExtremeReactors.MOD_ID + "." + registrationName,
                0xff000000 | tint, density, 300, 1000, lightLevel, rarity, false, true,
                CommonConstants.FLUID_TEXTURE_SOURCE_WATER, CommonConstants.FLUID_TEXTURE_FLOWING_WATER,
                CommonConstants.FLUID_TEXTURE_OVERLAY_WATER);
    }

    public static FluidDefinition of(final Reactants reactant) {
        return of(reactant.getReactantName(), reactant.getColour(), reactant.getFluidDensity(), reactant.getFluidLightLevel(), reactant.getRarity());
    }

    //region internals

    private ReactorFluidType() {
    }

    //endregion
}
