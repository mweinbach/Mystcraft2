/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.forgespi.language;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import ports.mekanism.platform.ModMetadata;

public final class IModInfo {

    private final ModMetadata metadata;
    private final ArtifactVersion version;
    private final IModFileInfo owningFile;

    public IModInfo(ModMetadata metadata) {
        this.metadata = metadata;
        this.version = new DefaultArtifactVersion(metadata.version());
        this.owningFile = new IModFileInfo(this, metadata.roots());
    }

    public String getModId() {
        return metadata.modId();
    }

    public String getNamespace() {
        return metadata.modId();
    }

    public String getDisplayName() {
        return metadata.displayName();
    }

    public ArtifactVersion getVersion() {
        return version;
    }

    public IModFileInfo getOwningFile() {
        return owningFile;
    }
}
