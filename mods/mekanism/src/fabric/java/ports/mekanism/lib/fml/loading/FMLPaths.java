/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml.loading;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import ports.mekanism.platform.Platform;

public enum FMLPaths {
    GAMEDIR,
    CONFIGDIR;

    public Path get() {
        return switch (this) {
            case GAMEDIR -> Platform.backend().gameDirectory();
            case CONFIGDIR -> Platform.backend().configDirectory();
        };
    }

    public static Path getOrCreateGameRelativePath(Path path, String name) {
        Path resolved = GAMEDIR.get().resolve(path);
        if (!Files.isDirectory(resolved)) {
            try {
                Files.createDirectories(resolved);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create directory " + name, e);
            }
        }
        return resolved;
    }
}
