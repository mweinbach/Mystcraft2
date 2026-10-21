/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.forgespi.locating;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IModFile {

    private final List<Path> roots;

    public IModFile(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    public List<Path> getRoots() {
        return roots;
    }

    /**
     * The first existing match among the roots, or a path in the first root that does not exist.
     */
    public Path findResource(String... pathName) {
        if (pathName.length == 0) {
            throw new IllegalArgumentException("Missing path");
        }
        Path fallback = null;
        for (Path root : roots) {
            Path path = root.resolve(String.join(root.getFileSystem().getSeparator(), pathName));
            if (Files.exists(path)) {
                return path;
            } else if (fallback == null) {
                fallback = path;
            }
        }
        return fallback == null ? Path.of(String.join("/", pathName)) : fallback;
    }
}
