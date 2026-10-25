package dev.icbm.port.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Vertical coloured light column used by the exothermic/endothermic beam blasts.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/entity/EntityLightBeam.java} (state machine) and
 * {@code content/blast/BlastBeam.java} (usage). The beam rises from five blocks below its position
 * to the build limit. Progress (0..1) scales the inner beam and outer glow radii; the server moves
 * progress toward a target by {@value #GROWTH_RATE} per tick and synchronizes it to clients, which
 * interpolate at the same rate. Starting the death cycle closes the beam and removes the entity once
 * closed; a five-minute safety limit always removes forgotten beams. Unlike upstream (which does not
 * save beam state) colour, sizes, progress, target, death cycle and age persist, so a beam reloaded
 * with its chunk still closes and expires.
 *
 * <p>The client renderer reuses the vanilla beacon beam texture; no upstream art is used.
 */
public final class LightBeamEntity extends Entity {
  public static final float GROWTH_RATE = 0.05F;
  /** Upstream safety removal: 20 ticks * 60 seconds * 5 minutes. */
  public static final int MAX_AGE = 20 * 60 * 5;

  private static final TrackedData<Float> PROGRESS =
      DataTracker.registerData(LightBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
  private static final TrackedData<Integer> COLOR =
      DataTracker.registerData(LightBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<Float> BEAM_SIZE =
      DataTracker.registerData(LightBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
  private static final TrackedData<Float> GLOW_SIZE =
      DataTracker.registerData(LightBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

  private float targetProgress = 1;
  private boolean deathCycle;
  /** Ticks after which the death cycle starts automatically; negative means never. */
  private int closeAfter = -1;
  private int beamAge;

  /** Client-side smoothed progress used by the renderer. */
  public float clientProgress;

  public LightBeamEntity(EntityType<? extends LightBeamEntity> type, World world) {
    super(type, world);
    noClip = true;
    ignoreCameraFrustum = true;
  }

  /**
   * Spawns a beam at {@code pos} with an RGB colour that opens to full size and starts closing
   * after {@code ticks} (clamped to the five-minute safety limit). Returns the spawned beam, or
   * null when the position is unloaded/invalid.
   */
  public static LightBeamEntity spawn(ServerWorld world, Vec3d pos, int rgb, int ticks) {
    if (!Double.isFinite(pos.x + pos.y + pos.z)
        || !world.isChunkLoaded(new net.minecraft.util.math.BlockPos(pos))) return null;
    LightBeamEntity beam = new LightBeamEntity(EntityContent.LIGHT_BEAM, world);
    beam.setPosition(pos);
    beam.setColor(rgb);
    beam.setSizes(1, 2);
    beam.setTargetProgress(1);
    beam.closeAfter = ticks < 0 ? -1 : Math.min(ticks, MAX_AGE);
    return world.spawnEntity(beam) ? beam : null;
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(PROGRESS, 0F);
    dataTracker.startTracking(COLOR, 0xFF0000);
    dataTracker.startTracking(BEAM_SIZE, 0.5F);
    dataTracker.startTracking(GLOW_SIZE, 1F);
  }

  public LightBeamEntity setColor(int rgb) {
    dataTracker.set(COLOR, rgb & 0xFFFFFF);
    return this;
  }

  public int getColor() {
    return dataTracker.get(COLOR);
  }

  public float[] colorComponents() {
    int rgb = getColor();
    return new float[] {((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F};
  }

  public LightBeamEntity setSizes(float beam, float glow) {
    dataTracker.set(BEAM_SIZE, MathHelper.clamp(beam, 0, 8));
    dataTracker.set(GLOW_SIZE, MathHelper.clamp(glow, 0, 8));
    return this;
  }

  public float getBeamSize() {
    return dataTracker.get(BEAM_SIZE);
  }

  public float getGlowSize() {
    return dataTracker.get(GLOW_SIZE);
  }

  public void setTargetProgress(float value) {
    targetProgress = MathHelper.clamp(value, 0, 1);
  }

  public float getTargetProgress() {
    return targetProgress;
  }

  public float getProgress() {
    return dataTracker.get(PROGRESS);
  }

  public void setProgress(float value) {
    dataTracker.set(PROGRESS, MathHelper.clamp(value, 0, 1));
  }

  /** Upstream {@code startDeathCycle()} plus closing target: animates shut, then removes. */
  public void close() {
    deathCycle = true;
    targetProgress = 0;
  }

  public void startDeathCycle() {
    deathCycle = true;
  }

  public boolean isClosing() {
    return deathCycle;
  }

  public int getBeamAge() {
    return beamAge;
  }

  @Override
  public void tick() {
    super.tick();
    if (world.isClient) {
      float progress = getProgress();
      if (clientProgress < progress) clientProgress = Math.min(progress, clientProgress + GROWTH_RATE);
      else if (clientProgress > progress)
        clientProgress = Math.max(progress, clientProgress - GROWTH_RATE);
      return;
    }
    beamAge++;
    if (closeAfter >= 0 && beamAge >= closeAfter && !deathCycle) close();
    float progress = getProgress();
    if (progress < targetProgress) setProgress(Math.min(targetProgress, progress + GROWTH_RATE));
    else if (progress > targetProgress) setProgress(Math.max(targetProgress, progress - GROWTH_RATE));
    if (deathCycle && Math.abs(getProgress() - targetProgress) <= 0.01F) discard();
    else if (beamAge > MAX_AGE) discard();
  }

  @Override
  public void move(MovementType type, Vec3d movement) {}

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public boolean isPushedByFluids() {
    return false;
  }

  @Override
  public boolean canHit() {
    return false;
  }

  @Override
  public boolean isImmuneToExplosion() {
    return true;
  }

  @Override
  public boolean damage(DamageSource source, float amount) {
    return false;
  }

  @Override
  public boolean shouldRender(double distance) {
    return distance < 256 * 256;
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    if (nbt.contains("Color")) setColor(nbt.getInt("Color"));
    if (nbt.contains("BeamSize")) setSizes(nbt.getFloat("BeamSize"), nbt.getFloat("GlowSize"));
    setProgress(nbt.getFloat("Progress"));
    targetProgress = MathHelper.clamp(nbt.getFloat("TargetProgress"), 0, 1);
    deathCycle = nbt.getBoolean("Closing");
    closeAfter = nbt.contains("CloseAfter") ? nbt.getInt("CloseAfter") : -1;
    beamAge = MathHelper.clamp(nbt.getInt("BeamAge"), 0, MAX_AGE + 1);
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    nbt.putInt("Color", getColor());
    nbt.putFloat("BeamSize", getBeamSize());
    nbt.putFloat("GlowSize", getGlowSize());
    nbt.putFloat("Progress", getProgress());
    nbt.putFloat("TargetProgress", targetProgress);
    nbt.putBoolean("Closing", deathCycle);
    nbt.putInt("CloseAfter", closeAfter);
    nbt.putInt("BeamAge", beamAge);
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  /** Lets callers such as blasts look up their beam after a reload. */
  public static java.util.List<LightBeamEntity> near(World world, Vec3d pos, double radius) {
    return world.getEntitiesByClass(
        LightBeamEntity.class,
        new net.minecraft.util.math.Box(pos, pos).expand(radius),
        beam -> !beam.isRemoved());
  }
}
