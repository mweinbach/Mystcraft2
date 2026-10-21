/*
 * Mekanism 1.19.2 port - loader services.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.platform;

import java.nio.file.Path;
import java.util.List;

/**
 * What the loader knows about an installed mod. {@code roots} are the directories or jar file systems holding its
 * classes and resources.
 */
public record ModMetadata(String modId, String displayName, String version, List<Path> roots) {
}
