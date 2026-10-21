/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml.loading;

import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.platform.Platform;

public final class FMLEnvironment {

    /**
     * Read when this class initializes, which happens after the platform backend is installed.
     */
    public static final Dist dist = Platform.dist();
    public static final boolean production = !Platform.backend().isDevelopmentEnvironment();

    private FMLEnvironment() {
    }
}
