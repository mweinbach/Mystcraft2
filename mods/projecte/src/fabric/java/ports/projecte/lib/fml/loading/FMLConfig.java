/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.loading;

/**
 * Forge's loader config. Fabric has no update checker, so version checks are off.
 */
public final class FMLConfig {

    private FMLConfig() {
    }

    public static boolean runVersionCheck() {
        return false;
    }
}
