/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import ports.mekanism.lib.fluids.FluidType;
import ports.mekanism.lib.fluids.FluidTypes;
import ports.mekanism.lib.registries.ForgeRegistries;
import ports.mekanism.lib.registries.RegistryObject;

/**
 * The entity attributes and fluid types Forge adds. On Fabric the attributes are registered under the
 * {@code mekanism} namespace so they cannot clash with another port's copy of Forge's; the port's mixins apply them
 * to players.
 */
public final class ForgeMod {

    public static final RegistryObject<Attribute> SWIM_SPEED = attribute("swim_speed");
    public static final RegistryObject<Attribute> REACH_DISTANCE = attribute("reach_distance");
    public static final RegistryObject<Attribute> STEP_HEIGHT_ADDITION = attribute("step_height_addition");

    public static final RegistryObject<FluidType> EMPTY_TYPE = fluidType("empty");
    public static final RegistryObject<FluidType> WATER_TYPE = fluidType("water");
    public static final RegistryObject<FluidType> LAVA_TYPE = fluidType("lava");

    private static boolean registered;

    private ForgeMod() {
    }

    private static RegistryObject<Attribute> attribute(String name) {
        return RegistryObject.create(new ResourceLocation("mekanism", name), ForgeRegistries.ATTRIBUTES);
    }

    private static RegistryObject<FluidType> fluidType(String name) {
        return RegistryObject.create(new ResourceLocation(name), FluidTypes.REGISTRY);
    }

    /**
     * Registers the attributes. Called once by the Fabric entrypoint before any Mekanism registry event.
     */
    public static synchronized void registerAttributes() {
        if (registered) {
            return;
        }
        registered = true;
        register(SWIM_SPEED, new RangedAttribute("forge.swimSpeed", 1.0D, 0.0D, 1024.0D).setSyncable(true));
        register(REACH_DISTANCE, new RangedAttribute("generic.reachDistance", 4.5D, 0.0D, 1024.0D).setSyncable(true));
        register(STEP_HEIGHT_ADDITION, new RangedAttribute("forge.stepHeight", 0.0D, -512.0D, 512.0D).setSyncable(true));
    }

    private static void register(RegistryObject<Attribute> object, Attribute attribute) {
        // Living attributes are built while this registry is already frozen. Open it for these Forge attributes only.
        if (Registry.ATTRIBUTE instanceof MappedRegistry<Attribute> mapped) {
            boolean frozen = mapped.frozen;
            mapped.frozen = false;
            try {
                Registry.register(Registry.ATTRIBUTE, object.getId(), attribute);
            } finally {
                mapped.frozen = frozen;
            }
        } else {
            Registry.register(Registry.ATTRIBUTE, object.getId(), attribute);
        }
    }
}
