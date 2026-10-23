/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.api;

import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.heat.IHeatHandler;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Public Fabric lookups for Mekanism's chemical and heat handlers.
 * <p>
 * Each id is one handler. The block lookup and the item lookup share that id and are different objects: blocks take a
 * {@link Direction} (the side being accessed, as Forge's {@code getCapability(cap, side)} does; null is the internal
 * side), and items take a {@link Void} context, which callers pass as null. The instance is the capability the port
 * attaches to that block entity or stack. Amounts are Mekanism's chemical units, the same longs Forge uses, not
 * Transfer API droplets. Heat is {@link IHeatHandler}, not a chemical.
 * <p>
 * {@code BlockApiLookup.get} and {@code ItemApiLookup.get} return these same instances when called with the id, the
 * handler class and the context class below, so a mod can depend on this class or rebuild the lookup from the id.
 * Query again when you need the handler; do not keep it after the block is removed or the stack is replaced.
 * Mekanism registers its own blocks and items. Other mods register theirs on the same lookups. When Mekanism asks a
 * foreign block entity or stack for a chemical or heat capability, it consults these lookups, so a provider must not
 * answer by asking that block entity or stack for the same Mekanism capability.
 */
public final class MekanismChemicalLookups {

    public static final ResourceLocation GAS_ID = new ResourceLocation("mekanism", "gas_handler");
    public static final ResourceLocation INFUSION_ID = new ResourceLocation("mekanism", "infusion_handler");
    public static final ResourceLocation PIGMENT_ID = new ResourceLocation("mekanism", "pigment_handler");
    public static final ResourceLocation SLURRY_ID = new ResourceLocation("mekanism", "slurry_handler");
    public static final ResourceLocation HEAT_ID = new ResourceLocation("mekanism", "heat_handler");

    public static final BlockApiLookup<IGasHandler, Direction> GAS = BlockApiLookup.get(GAS_ID, IGasHandler.class, Direction.class);
    public static final BlockApiLookup<IInfusionHandler, Direction> INFUSION = BlockApiLookup.get(INFUSION_ID, IInfusionHandler.class, Direction.class);
    public static final BlockApiLookup<IPigmentHandler, Direction> PIGMENT = BlockApiLookup.get(PIGMENT_ID, IPigmentHandler.class, Direction.class);
    public static final BlockApiLookup<ISlurryHandler, Direction> SLURRY = BlockApiLookup.get(SLURRY_ID, ISlurryHandler.class, Direction.class);
    public static final BlockApiLookup<IHeatHandler, Direction> HEAT = BlockApiLookup.get(HEAT_ID, IHeatHandler.class, Direction.class);

    public static final ItemApiLookup<IGasHandler, Void> GAS_ITEM = ItemApiLookup.get(GAS_ID, IGasHandler.class, Void.class);
    public static final ItemApiLookup<IInfusionHandler, Void> INFUSION_ITEM = ItemApiLookup.get(INFUSION_ID, IInfusionHandler.class, Void.class);
    public static final ItemApiLookup<IPigmentHandler, Void> PIGMENT_ITEM = ItemApiLookup.get(PIGMENT_ID, IPigmentHandler.class, Void.class);
    public static final ItemApiLookup<ISlurryHandler, Void> SLURRY_ITEM = ItemApiLookup.get(SLURRY_ID, ISlurryHandler.class, Void.class);
    public static final ItemApiLookup<IHeatHandler, Void> HEAT_ITEM = ItemApiLookup.get(HEAT_ID, IHeatHandler.class, Void.class);

    private MekanismChemicalLookups() {
    }
}
