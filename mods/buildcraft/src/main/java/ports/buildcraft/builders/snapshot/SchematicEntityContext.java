package ports.buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Upstream {@code SchematicEntityContext}: the entity being scanned and the scanned box's minimum corner. */
public record SchematicEntityContext(Level level, BlockPos basePos, Entity entity) {}
