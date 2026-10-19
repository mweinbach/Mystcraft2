/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;

/**
 * Loader-neutral description of one of our fluids. Forge turns it into a {@code FluidType}; Fabric into fluid
 * variant attributes and a fluid render handler.
 *
 * @param name           registration name of the source fluid
 * @param descriptionId  translation key of the fluid name
 * @param tint           ARGB tint applied to the textures
 * @param density        in kg/m^3; negative or very small values make the fluid rise
 * @param temperature    in Kelvin
 * @param lightLevel     0 to 15
 * @param lighterThanAir true for gases such as steam
 * @param swimmable      true if entities can swim, drown and be pushed in it
 */
public record FluidDefinition(String name, String descriptionId, int tint, int density, int temperature, int viscosity,
                              int lightLevel, Rarity rarity, boolean lighterThanAir, boolean swimmable,
                              ResourceLocation stillTexture, ResourceLocation flowingTexture,
                              ResourceLocation overlayTexture) {
}
