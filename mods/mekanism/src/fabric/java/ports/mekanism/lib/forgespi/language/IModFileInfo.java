/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.forgespi.language;

import java.nio.file.Path;
import java.util.List;
import ports.mekanism.lib.forgespi.locating.IModFile;

public final class IModFileInfo {

    private final List<IModInfo> mods;
    private final IModFile file;

    IModFileInfo(IModInfo mod, List<Path> roots) {
        this.mods = List.of(mod);
        this.file = new IModFile(roots);
    }

    public List<IModInfo> getMods() {
        return mods;
    }

    public IModFile getFile() {
        return file;
    }
}
