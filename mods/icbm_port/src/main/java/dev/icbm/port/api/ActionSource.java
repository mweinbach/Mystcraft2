package dev.icbm.port.api;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Stable attribution, independent of whether the original projectile is still loaded. */
public record ActionSource(
    @Nullable UUID directEntity,
    @Nullable UUID owner,
    Identifier cause,
    @Nullable BlockPos origin) {
  public static final Identifier UNKNOWN = new Identifier("icbm_port", "unknown");

  public ActionSource {
    if (cause == null) throw new IllegalArgumentException("cause");
    if (origin != null) origin = origin.toImmutable();
  }

  public static ActionSource of(@Nullable Entity entity) {
    Entity owner =
        entity instanceof ProjectileEntity projectile
            ? projectile.getOwner()
            : entity instanceof dev.icbm.port.entity.PrimedExplosiveEntity primed
                ? primed.getOwner()
                : entity;
    return new ActionSource(
        entity == null ? null : entity.getUuid(),
        owner == null ? null : owner.getUuid(),
        new Identifier("icbm_port", entity == null ? "environment" : "entity"),
        entity == null ? null : entity.getBlockPos());
  }

  public static ActionSource command(@Nullable Entity entity, BlockPos origin) {
    ActionSource source = of(entity);
    return new ActionSource(
        source.directEntity, source.owner, new Identifier("icbm_port", "command"), origin);
  }

  @Nullable
  public Entity resolve(ServerWorld world) {
    Entity entity = owner == null ? null : world.getEntity(owner);
    return entity != null ? entity : directEntity == null ? null : world.getEntity(directEntity);
  }

  public NbtCompound toNbt() {
    NbtCompound nbt = new NbtCompound();
    if (directEntity != null) nbt.putUuid("Direct", directEntity);
    if (owner != null) nbt.putUuid("Owner", owner);
    nbt.putString("Cause", cause.toString());
    if (origin != null) nbt.putLong("Origin", origin.asLong());
    return nbt;
  }

  public static ActionSource fromNbt(NbtCompound nbt) {
    Identifier cause = Identifier.tryParse(nbt.getString("Cause"));
    return new ActionSource(
        nbt.containsUuid("Direct") ? nbt.getUuid("Direct") : null,
        nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null,
        cause == null ? UNKNOWN : cause,
        nbt.contains("Origin") ? BlockPos.fromLong(nbt.getLong("Origin")) : null);
  }
}
