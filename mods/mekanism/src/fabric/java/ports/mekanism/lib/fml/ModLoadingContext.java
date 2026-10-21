/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * The module whose code is running. The port's lifecycle driver sets it around each module's constructor and
 * lifecycle events, as Forge does per mod.
 */
public final class ModLoadingContext {

    private static final ModLoadingContext INSTANCE = new ModLoadingContext();

    @Nullable
    private ModContainer activeContainer;

    private ModLoadingContext() {
    }

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public ModContainer getActiveContainer() {
        return Objects.requireNonNull(activeContainer, "No mod is being loaded");
    }

    public String getActiveNamespace() {
        return activeContainer == null ? "minecraft" : activeContainer.getNamespace();
    }

    public void setActiveContainer(@Nullable ModContainer container) {
        this.activeContainer = container;
    }
}
