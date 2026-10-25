package dev.icbm.port.exposure;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import java.util.ArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

/** Independent adaptation of gas toxicity, armor and radioactive block source behavior. */
public final class ExposureContent {
  public static ExposureConfig config = new ExposureConfig();
  public static final RadioactiveBlock RADIOACTIVE_STONE = new RadioactiveBlock(Blocks.STONE);
  public static final RadioactiveBlock RADIOACTIVE_DIRT = new RadioactiveBlock(Blocks.DIRT);

  private ExposureContent() {}

  private static void block(String id, Block block) {
    Registry.register(Registry.BLOCK, IcbmPort.id(id), block);
    Registry.register(
        Registry.ITEM,
        IcbmPort.id(id),
        new BlockItem(block, new Item.Settings().group(IcbmPort.GROUP)));
  }

  public static void register() {
    config = ExposureConfig.load();
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register(
        (entity, world) -> {
          if (entity instanceof LivingEntity)
            ExposureState.get(world).onRemoval(entity.getUuid(), entity.getRemovalReason());
        });
    block("radioactive_stone", RADIOACTIVE_STONE);
    block("radioactive_dirt", RADIOACTIVE_DIRT);
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          for (ServerWorld world : server.getWorlds()) tickFallout(world);
          if (server.getTicks() % 20 != 0) return;
          ExposureState state = ExposureState.get(server.getOverworld());
          for (var entry : new ArrayList<>(state.doses.entrySet())) {
            LivingEntity entity = null;
            for (ServerWorld world : server.getWorlds())
              if (world.getEntity(entry.getKey()) instanceof LivingEntity living) {
                entity = living;
                break;
              }
            if (entity == null) continue;
            if (!entity.isAlive()) {
              state.clear(entry.getKey());
              continue;
            }
            ExposureState.Dose dose = entry.getValue();
            // Source gas counters were blast-local; gradual recovery bounds persistent counters.
            dose.chemicalHits = Math.max(0, dose.chemicalHits - 1);
            dose.radiationHits = Math.max(0, dose.radiationHits - 1);
            if (dose.infectiousTicks > 0) {
              dose.infectiousTicks = Math.max(0, dose.infectiousTicks - 20);
              if (config.viralSpread
                  && server.getTicks() % Math.max(20, config.viralSpreadInterval) == 0) {
                ServerWorld world = (ServerWorld) entity.world;
                for (LivingEntity other :
                    world.getEntitiesByClass(
                        LivingEntity.class,
                        entity
                            .getBoundingBox()
                            .expand(Math.max(0, Math.min(8, config.viralSpreadRange))),
                        e -> e.isAlive()))
                  if (other != entity
                      && (ExposureState.get(world).find(other.getUuid()) == null
                          || ExposureState.get(world).find(other.getUuid()).infectiousTicks == 0))
                    expose(other, PayloadType.CONTAGIOUS);
              }
            } else dose.viralHits = Math.max(0, dose.viralHits - 1);
            if (dose.chemicalHits == 0
                && dose.viralHits == 0
                && dose.radiationHits == 0
                && dose.infectiousTicks == 0) state.clear(entry.getKey());
            else state.markDirty();
          }
        });
  }

  /**
   * Vanilla particles are emitted server-side to tracking clients; no custom texture registration.
   */
  public static void registerClient() {}

  public static float protection(LivingEntity entity) {
    if (config.requireHelmet && entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) return 0;
    float rating = 0;
    for (ItemStack stack : entity.getArmorItems()) {
      Float value = config.armorRatings.get(Registry.ITEM.getId(stack.getItem()).toString());
      if (value != null && Float.isFinite(value)) rating += Math.max(0, Math.min(1, value));
    }
    return Math.max(0, Math.min(1, rating));
  }

  public static boolean protectedFrom(LivingEntity entity, float threshold) {
    if (entity instanceof PlayerEntity player && (player.isCreative() || player.isSpectator()))
      return true;
    float rating = protection(entity);
    return rating >= threshold && rating >= entity.getRandom().nextFloat();
  }

  /** Chemical gas damage; like upstream {@code icbm.chemical} it is not armor-bypassing. */
  public static final DamageSource CHEMICAL_DAMAGE = new GasDamageSource("icbm_port.chemical");

  /** Viral gas damage; like upstream {@code icbm.contagious} it is not armor-bypassing. */
  public static final DamageSource CONTAGIOUS_DAMAGE =
      new GasDamageSource("icbm_port.contagious");

  private static final class GasDamageSource extends DamageSource {
    GasDamageSource(String name) {
      super(name);
    }
  }

  /** Upstream {@code minGasProtection()} per gas family ({@code ConfigMain.protectiveArmor}). */
  public static float gasProtectionThreshold(PayloadType payload) {
    return switch (payload) {
      case DEBILITATION -> config.debilitationProtection;
      case CHEMICAL -> config.chemicalProtection;
      case CONTAGIOUS -> config.viralProtection;
      default -> 1;
    };
  }

  /**
   * Upstream {@code BlastGasBase.canGasEffect} plus the chemical/contagious invulnerability
   * checks: living, not a creative (or spectator) player, not immune to the gas damage source.
   */
  public static boolean gasCanAffect(LivingEntity entity, PayloadType payload) {
    if (!entity.isAlive()) return false;
    if (entity instanceof PlayerEntity player && (player.isCreative() || player.isSpectator()))
      return false;
    return switch (payload) {
      case CHEMICAL -> !entity.isInvulnerableTo(CHEMICAL_DAMAGE);
      case CONTAGIOUS -> !entity.isInvulnerableTo(CONTAGIOUS_DAMAGE);
      default -> true;
    };
  }

  /**
   * Persistent-record exposure used by viral spread and API callers: rolls protection, then
   * counts hits in the entity's saved dose record.
   */
  public static void expose(LivingEntity entity, PayloadType payload) {
    if (!(entity.world instanceof ServerWorld world) || !entity.isAlive()) return;
    if (protectedFrom(entity, gasProtectionThreshold(payload))) return;
    ExposureState.Dose dose = ExposureState.get(world).dose(entity.getUuid());
    int hits =
        switch (payload) {
          case CHEMICAL -> dose.chemicalHits = Math.min(100000, dose.chemicalHits + 1);
          case CONTAGIOUS -> dose.viralHits = Math.min(100000, dose.viralHits + 1);
          default -> 1;
        };
    affect(world, entity, payload, hits, dose);
  }

  /**
   * Gas-cloud exposure, adapted from MIT ICBM-Classic {@code BlastGasBase.doExplode} and the
   * {@code applyEffect} overrides in {@code BlastDebilitation}, {@code BlastChemical} and {@code
   * BlastContagious}. The caller has already rolled protection and supplies the cloud-local hit
   * count (upstream {@code impactedEntityMap}). Viral exposure still marks the persistent record
   * infectious so the documented spread extension works.
   */
  public static void applyGas(LivingEntity entity, PayloadType payload, int hitCount) {
    if (!(entity.world instanceof ServerWorld world) || !entity.isAlive()) return;
    ExposureState.Dose dose =
        payload == PayloadType.CONTAGIOUS
            ? ExposureState.get(world).dose(entity.getUuid())
            : null;
    if (dose != null) dose.viralHits = Math.max(dose.viralHits, Math.min(100000, hitCount));
    affect(world, entity, payload, hitCount, dose);
  }

  private static void affect(
      ServerWorld world,
      LivingEntity entity,
      PayloadType payload,
      int hits,
      ExposureState.Dose dose) {
    switch (payload) {
      case DEBILITATION -> {
        effect(entity, StatusEffects.POISON, 360, 0);
        effect(entity, StatusEffects.MINING_FATIGUE, 1200, 0);
        effect(entity, StatusEffects.SLOWNESS, 1200, 2);
      }
      case CHEMICAL -> {
        effect(entity, StatusEffects.POISON, 600, 0);
        effect(entity, StatusEffects.NAUSEA, 600, 0);
        commonEffects(entity, 0);
        if (hits > config.chemicalToxicityStart)
          entity.damage(
              CHEMICAL_DAMAGE,
              Math.max(config.chemicalToxicityMinDamage, hits * config.chemicalToxicityScale));
      }
      case CONTAGIOUS -> {
        if (dose != null) dose.infectiousTicks = 900;
        effect(entity, StatusEffects.POISON, 900, 3);
        effect(entity, StatusEffects.BLINDNESS, 300, 3);
        commonEffects(entity, 3);
        if (dev.icbm.port.payload.Payloads.applyMutation(entity))
          ExposureState.get(world).clear(entity.getUuid());
        else if (hits > config.viralToxicityStart)
          entity.damage(
              CONTAGIOUS_DAMAGE,
              Math.max(config.viralToxicityMinDamage, hits * config.viralToxicityScale));
      }
      default -> {}
    }
    world.spawnParticles(
        payload == PayloadType.DEBILITATION ? ParticleTypes.CLOUD : ParticleTypes.ENTITY_EFFECT,
        entity.getX(),
        entity.getBodyY(.5),
        entity.getZ(),
        2,
        .2,
        .2,
        .2,
        .01);
  }

  private static void commonEffects(LivingEntity entity, int amplifier) {
    effect(entity, StatusEffects.HUNGER, 600, amplifier);
    effect(entity, StatusEffects.WEAKNESS, 700, amplifier);
    effect(entity, StatusEffects.MINING_FATIGUE, 1200, amplifier);
  }

  private static void effect(LivingEntity entity, StatusEffect effect, int ticks, int amplifier) {
    entity.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier));
  }

  public static void clear(LivingEntity entity) {
    if (entity.world instanceof ServerWorld world) ExposureState.get(world).clear(entity.getUuid());
  }

  public static void radiate(ServerWorld world, BlockPos pos) {
    int range = Math.max(0, Math.min(32, config.decayRange));
    if (range == 0 || config.effectChance <= 0) return;
    for (LivingEntity entity :
        world.getEntitiesByClass(
            LivingEntity.class, new Box(pos).expand(range), e -> e.isAlive())) {
      if (world.random.nextFloat() >= config.effectChance
          || protectedFrom(entity, config.radiationProtection)) continue;
      ExposureState.Dose dose = ExposureState.get(world).dose(entity.getUuid());
      dose.radiationHits = Math.min(100000, dose.radiationHits + 1);
      entity.damage(DamageSource.MAGIC, Math.max(0, config.radiationDamage));
      if (config.witherDuration > 0)
        effect(entity, StatusEffects.WITHER, Math.min(12000, config.witherDuration), 0);
    }
    world.spawnParticles(
        ParticleTypes.ASH, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 1, .3, .2, .3, .01);
  }

  /**
   * Loaded-only, server-thread fallout, processed at 4096 positions/tick; never force-loads chunks.
   */
  public static void contaminate(ServerWorld world, Vec3d center, double radius) {
    if (!Double.isFinite(radius) || radius <= 0) return;
    FalloutState state = FalloutState.get(world);
    if (state.jobs.size() >= 64) return;
    int r = (int) Math.ceil(Math.min(128, radius));
    state.jobs.add(new FalloutState.Job(new BlockPos(center), r, 0));
    state.markDirty();
  }

  private static void tickFallout(ServerWorld world) {
    FalloutState state = FalloutState.get(world);
    int budget = 4096;
    while (budget > 0 && !state.jobs.isEmpty()) {
      FalloutState.Job job = state.jobs.removeFirst();
      long cursor = job.cursor();
      while (budget-- > 0 && cursor < job.volume()) {
        long index = cursor++;
        int d = job.diameter(), r = job.radius();
        int x = (int) (index % d) - r,
            y = (int) ((index / d) % d) - r,
            z = (int) (index / ((long) d * d)) - r;
        if (x * x + y * y + z * z > r * r) continue;
        BlockPos pos = job.origin().add(x, y, z);
        if (world.isOutOfHeightLimit(pos) || !world.isChunkLoaded(pos)) continue;
        replace(world, pos);
      }
      if (cursor < job.volume())
        state.jobs.addFirst(new FalloutState.Job(job.origin(), job.radius(), cursor));
      state.markDirty();
    }
  }

  public static boolean replace(ServerWorld world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    Block replacement = null;
    if (state.isOf(Blocks.STONE)) replacement = RADIOACTIVE_STONE;
    else if (state.isOf(Blocks.DIRT)
        || state.isOf(Blocks.FARMLAND)
        || state.isOf(Blocks.DIRT_PATH)
        || state.isOf(Blocks.GRASS_BLOCK)
        || state.isOf(Blocks.MYCELIUM)) replacement = RADIOACTIVE_DIRT;
    if (replacement != null && world.random.nextFloat() < .2f) {
      world.setBlockState(pos, replacement.getDefaultState(), 3);
      return true;
    }
    if (state.isIn(BlockTags.LEAVES)
        || state.getMaterial() == Material.PLANT
        || state.getMaterial() == Material.REPLACEABLE_PLANT) {
      world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
      return true;
    }
    return false;
  }
}
