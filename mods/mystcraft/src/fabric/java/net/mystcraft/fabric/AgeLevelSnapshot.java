package net.mystcraft.fabric;

/** Added to {@code MinecraftServer} by {@code MinecraftServerAgeMixin}; the Fabric counterpart of Forge's {@code markWorldsDirty}. */
public interface AgeLevelSnapshot {
    /** Discards the cached {@code getAllLevels} snapshot after the level map changed. */
    void mystcraft$levelsChanged();
}
