/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.util.Set;
import mekanism.common.capabilities.Capabilities;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.mekanism.fabric.api.MekanismChemicalLookups;
import ports.mekanism.lib.common.capabilities.Capability;

/**
 * Publishes the port's chemical and heat capabilities on {@link MekanismChemicalLookups}. Transfer between Mekanism
 * blocks already uses those capabilities directly; this only lets other mods see them.
 * <p>
 * The item fallback answers only for the port's own items. A foreign stack is left to the provider that mod
 * registered: a foreign stack's missing capability falls back to these lookups, so asking it here would recurse.
 */
final class FabricChemicals {

    private FabricChemicals() {
    }

    static void register(BlockEntityType<?>[] types, Set<String> namespaces) {
        registerBlocks(MekanismChemicalLookups.GAS, Capabilities.GAS_HANDLER, types);
        registerBlocks(MekanismChemicalLookups.INFUSION, Capabilities.INFUSION_HANDLER, types);
        registerBlocks(MekanismChemicalLookups.PIGMENT, Capabilities.PIGMENT_HANDLER, types);
        registerBlocks(MekanismChemicalLookups.SLURRY, Capabilities.SLURRY_HANDLER, types);
        registerBlocks(MekanismChemicalLookups.HEAT, Capabilities.HEAT_HANDLER, types);
        Set<String> mods = Set.copyOf(namespaces);
        registerItems(MekanismChemicalLookups.GAS_ITEM, Capabilities.GAS_HANDLER, mods);
        registerItems(MekanismChemicalLookups.INFUSION_ITEM, Capabilities.INFUSION_HANDLER, mods);
        registerItems(MekanismChemicalLookups.PIGMENT_ITEM, Capabilities.PIGMENT_HANDLER, mods);
        registerItems(MekanismChemicalLookups.SLURRY_ITEM, Capabilities.SLURRY_HANDLER, mods);
        registerItems(MekanismChemicalLookups.HEAT_ITEM, Capabilities.HEAT_HANDLER, mods);
    }

    private static <T> void registerBlocks(BlockApiLookup<T, Direction> lookup, Capability<T> capability, BlockEntityType<?>... types) {
        if (types.length == 0) {
            return;
        }
        lookup.registerForBlockEntities((blockEntity, side) -> blockEntity.getCapability(capability, side).orElse(null), types);
    }

    private static <T> void registerItems(ItemApiLookup<T, Void> lookup, Capability<T> capability, Set<String> namespaces) {
        lookup.registerFallback((stack, context) -> owned(namespaces, stack) ? stack.getCapability(capability).orElse(null) : null);
    }

    private static boolean owned(Set<String> namespaces, ItemStack stack) {
        return !stack.isEmpty() && namespaces.contains(Registry.ITEM.getKey(stack.getItem()).getNamespace());
    }
}
