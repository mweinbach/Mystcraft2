package net.mystcraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.mystcraft.fabric.AgeLevelSnapshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Runtime age creation may occur while a dimension is ticking, so callers iterate a stable snapshot.
 * The snapshot is cached until the level map changes, and other mods' injections into
 * {@code getAllLevels} still run.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerAgeMixin implements AgeLevelSnapshot {
    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;
    @Unique private volatile List<ServerLevel> mystcraft$levelSnapshot;

    @ModifyReturnValue(method = "getAllLevels", at = @At("RETURN"))
    private Iterable<ServerLevel> mystcraft$snapshotLevels(Iterable<ServerLevel> original) {
        // Respect another mod that already replaced the vanilla live view.
        if (original != levels.values()) return original;
        List<ServerLevel> snapshot = mystcraft$levelSnapshot;
        // The size check also catches mods that add or remove levels without notifying Mystcraft.
        if (snapshot == null || snapshot.size() != levels.size()) {
            snapshot = List.copyOf(levels.values());
            mystcraft$levelSnapshot = snapshot;
        }
        return snapshot;
    }

    @Override public void mystcraft$levelsChanged() {
        mystcraft$levelSnapshot = null;
    }
}
