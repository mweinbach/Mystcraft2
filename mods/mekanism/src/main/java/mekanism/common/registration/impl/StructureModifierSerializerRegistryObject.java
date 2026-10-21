package mekanism.common.registration.impl;

import com.mojang.serialization.Codec;
import mekanism.common.registration.WrappedRegistryObject;
import ports.mekanism.lib.common.world.StructureModifier;
import ports.mekanism.lib.registries.RegistryObject;

public class StructureModifierSerializerRegistryObject<T extends StructureModifier> extends WrappedRegistryObject<Codec<T>> {

    public StructureModifierSerializerRegistryObject(RegistryObject<Codec<T>> registryObject) {
        super(registryObject);
    }
}