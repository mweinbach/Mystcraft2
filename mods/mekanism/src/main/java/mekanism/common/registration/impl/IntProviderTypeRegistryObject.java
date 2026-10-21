package mekanism.common.registration.impl;

import mekanism.common.registration.WrappedRegistryObject;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviderType;
import ports.mekanism.lib.registries.RegistryObject;

public class IntProviderTypeRegistryObject<PROVIDER extends IntProvider> extends WrappedRegistryObject<IntProviderType<PROVIDER>> {

    public IntProviderTypeRegistryObject(RegistryObject<IntProviderType<PROVIDER>> registryObject) {
        super(registryObject);
    }
}