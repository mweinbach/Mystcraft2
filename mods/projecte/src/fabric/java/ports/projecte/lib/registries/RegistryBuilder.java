/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The options of a custom registry. The loader backend creates the registry from them: a Forge registry on Forge,
 * a synced vanilla registry through Fabric's registry builder on Fabric.
 */
public class RegistryBuilder<T> {

    @Nullable
    private ResourceLocation name;
    @Nullable
    private ResourceLocation defaultKey;
    private boolean hasTags;
    private boolean saveToDisc = true;
    private boolean sync = true;

    public RegistryBuilder<T> setName(ResourceLocation name) {
        this.name = name;
        return this;
    }

    public RegistryBuilder<T> setDefaultKey(ResourceLocation key) {
        this.defaultKey = key;
        return this;
    }

    public RegistryBuilder<T> hasTags() {
        this.hasTags = true;
        return this;
    }

    public RegistryBuilder<T> disableSaving() {
        this.saveToDisc = false;
        return this;
    }

    public RegistryBuilder<T> disableSync() {
        this.sync = false;
        return this;
    }

    @Nullable
    public ResourceLocation getName() {
        return name;
    }

    @Nullable
    public ResourceLocation getDefault() {
        return defaultKey;
    }

    public boolean getHasTags() {
        return hasTags;
    }

    public boolean getSaveToDisc() {
        return saveToDisc;
    }

    public boolean getSync() {
        return sync;
    }
}
