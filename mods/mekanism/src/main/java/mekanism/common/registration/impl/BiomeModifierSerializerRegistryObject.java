package mekanism.common.registration.impl;

import com.mojang.serialization.Codec;
import mekanism.common.registration.WrappedRegistryObject;
import ports.mekanism.lib.common.world.BiomeModifier;
import ports.mekanism.lib.registries.RegistryObject;

public class BiomeModifierSerializerRegistryObject<T extends BiomeModifier> extends WrappedRegistryObject<Codec<T>> {

    public BiomeModifierSerializerRegistryObject(RegistryObject<Codec<T>> registryObject) {
        super(registryObject);
    }
}