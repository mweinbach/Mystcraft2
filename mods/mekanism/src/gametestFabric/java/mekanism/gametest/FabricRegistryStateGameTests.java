/*
 * Mekanism 1.19.2 port - Fabric-only in-world tests.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ports.mekanism.fabric.FabricLifecycle;

/**
 * Mekanism fills the worldgen registries during its own initialisation, while mods initialised after it still register
 * their features there. In Tekxit 4 an unconditional freeze crashed MineCells, Tech Reborn, Spectrum and Terrestria
 * with "Registry is already frozen"; the registration must leave each registry as it found it.
 */
public class FabricRegistryStateGameTests {

    @GameTest(template = TEMPLATE)
    public static void worldgenRegistrationRestoresFrozenState(GameTestHelper helper) {
        MappedRegistry<String> registry = new MappedRegistry<>(
              ResourceKey.createRegistryKey(new ResourceLocation("mekanism_gametest", "registry_state")), Lifecycle.stable(), null);
        ResourceLocation first = new ResourceLocation("mekanism_gametest", "first");
        ResourceLocation second = new ResourceLocation("mekanism_gametest", "second");

        FabricLifecycle.whileWritable(registry, () -> Registry.register(registry, first, "first"));
        check(registry.containsKey(first), "Registration into an open registry did not run");
        check(!registry.frozen, "An open registry was frozen by Mekanism's registration; mods initialised later cannot register");

        registry.freeze();
        FabricLifecycle.whileWritable(registry, () -> Registry.register(registry, second, "second"));
        check(registry.containsKey(second), "A frozen registry was not opened for Mekanism's registration");
        check(registry.frozen, "A registry that was frozen before Mekanism's registration was left open");
        helper.succeed();
    }
}
