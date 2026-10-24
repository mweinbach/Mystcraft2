/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.fluids.FluidStack;
import ports.projecte.lib.registries.IForgeRegistry;
import ports.projecte.lib.registries.RegistryManager;

/**
 * Methods Forge adds to {@link FriendlyByteBuf}, injected into it on Fabric. Registry entries travel by name rather
 * than by Forge's synced numeric ids; only Fabric peers read them.
 */
public interface IForgeFriendlyByteBuf {

    private FriendlyByteBuf self() {
        return (FriendlyByteBuf) this;
    }

    default <T> void writeRegistryIdUnsafe(@NotNull IForgeRegistry<T> registry, @NotNull T entry) {
        writeRegistryIdUnsafe(registry, Objects.requireNonNull(registry.getKey(entry), () -> "Unregistered entry " + entry));
    }

    default void writeRegistryIdUnsafe(@NotNull IForgeRegistry<?> registry, @NotNull ResourceLocation entryKey) {
        self().writeResourceLocation(entryKey);
    }

    default <T> T readRegistryIdUnsafe(@NotNull IForgeRegistry<T> registry) {
        return registry.getValue(self().readResourceLocation());
    }

    default <T> void writeRegistryId(@NotNull IForgeRegistry<T> registry, @NotNull T entry) {
        self().writeResourceLocation(registry.getRegistryName());
        writeRegistryIdUnsafe(registry, entry);
    }

    default <T> T readRegistryId() {
        ResourceLocation registryName = self().readResourceLocation();
        IForgeRegistry<T> registry = RegistryManager.ACTIVE.getRegistry(registryName);
        if (registry == null) {
            throw new IllegalArgumentException("Unknown registry " + registryName);
        }
        return readRegistryIdUnsafe(registry);
    }

    default <T> T readRegistryIdSafe(Class<? super T> registrySuperType) {
        T value = readRegistryId();
        if (!registrySuperType.isInstance(value)) {
            throw new IllegalArgumentException("Attempted to read a registry entry of the wrong type, expected " + registrySuperType + " got " + value);
        }
        return value;
    }

    default void writeFluidStack(FluidStack stack) {
        stack.writeToPacket(self());
    }

    default FluidStack readFluidStack() {
        return FluidStack.readFromPacket(self());
    }
}
