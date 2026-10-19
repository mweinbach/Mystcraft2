package dev.icbm.port.client;

import dev.icbm.port.effects.BlastEffects;
import dev.icbm.port.payload.PayloadType;
import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/** Bounded, shader-independent shockwaves and rising blast plumes for every delivery form. */
public final class BlastEffectsClient {
  private static final ArrayDeque<Effect> ACTIVE = new ArrayDeque<>();
  private static ClientWorld lastWorld;

  private BlastEffectsClient() {}

  public static void register() {
    ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
        .registerReloadListener(new PayloadVisuals());
    ClientPlayNetworking.registerGlobalReceiver(
        BlastEffects.DETONATION,
        (client, handler, packet, response) -> {
          PayloadType type = packet.readEnumConstant(PayloadType.class);
          Vec3d position = new Vec3d(packet.readDouble(), packet.readDouble(), packet.readDouble());
          float radius = packet.readFloat();
          client.execute(
              () -> {
                if (client.world == null || !Float.isFinite(radius)) return;
                if (client.world != lastWorld) ACTIVE.clear();
                lastWorld = client.world;
                if (ACTIVE.size() >= 12) ACTIVE.removeFirst();
                ACTIVE.addLast(new Effect(position, type, Math.max(1, Math.min(80, radius))));
                sound(client.world, position, type);
              });
        });
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (client.world == null || client.world != lastWorld) {
            ACTIVE.clear();
            lastWorld = client.world;
            return;
          }
          // Every effect has a fixed particle count per tick and a finite lifetime.
          ACTIVE.removeIf(effect -> effect.tick(client.world));
        });
  }

  private static void sound(ClientWorld world, Vec3d position, PayloadType type) {
    PayloadVisuals.Profile profile = PayloadVisuals.get(type);
    world.playSound(
        position.x,
        position.y,
        position.z,
        soundEvent(profile.sound()),
        SoundCategory.BLOCKS,
        type.tier() >= 3 ? 3f : 1.8f,
        profile.shape() == PayloadVisuals.Shape.VORTEX ? .45f : .75f,
        false);
  }

  private static SoundEvent soundEvent(PayloadVisuals.Sound sound) {
    return switch (sound) {
      case SONIC_BOOM -> SoundEvents.ENTITY_WARDEN_SONIC_BOOM;
      case ANCHOR -> SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE;
      case TELEPORT -> SoundEvents.ENTITY_ENDERMAN_TELEPORT;
      case TWINKLE -> SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE;
      case FIRECHARGE -> SoundEvents.ITEM_FIRECHARGE_USE;
      case GLASS -> SoundEvents.BLOCK_GLASS_BREAK;
      case EXPLODE -> SoundEvents.ENTITY_GENERIC_EXPLODE;
    };
  }

  private static ParticleEffect particle(PayloadVisuals.Particle kind) {
    return switch (kind) {
      case FLAME -> ParticleTypes.FLAME;
      case SMOKE -> ParticleTypes.SMOKE;
      case SNOWFLAKE -> ParticleTypes.SNOWFLAKE;
      case REVERSE_PORTAL -> ParticleTypes.REVERSE_PORTAL;
      case ELECTRIC_SPARK -> ParticleTypes.ELECTRIC_SPARK;
      case SNEEZE -> ParticleTypes.SNEEZE;
      case END_ROD -> ParticleTypes.END_ROD;
      case PORTAL -> ParticleTypes.PORTAL;
    };
  }

  private static final class Effect {
    private final Vec3d center;
    private final PayloadType type;
    private final double radius;
    private final DustParticleEffect dust;
    private int age;

    private Effect(Vec3d center, PayloadType type, double radius) {
      this.center = center;
      this.type = type;
      this.radius = Math.min(32, Math.max(3, radius));
      PayloadVisuals.Profile profile = PayloadVisuals.get(type);
      dust = new DustParticleEffect(profile.color(), type.tier() >= 3 ? 2.5f : 1.5f);
    }

    private boolean tick(ClientWorld world) {
      PayloadVisuals.Profile profile = PayloadVisuals.get(type);
      if (++age > (profile.shape() == PayloadVisuals.Shape.MUSHROOM ? 100 : 40)) return true;
      if (age <= 22) ring(world);
      if (profile.shape() == PayloadVisuals.Shape.MUSHROOM) mushroom(world);
      else if (age <= 16) extra(world, profile);
      return false;
    }

    private void ring(ClientWorld world) {
      double reach = radius * age / 22;
      for (int i = 0; i < 20; i++) {
        double angle = i * Math.PI * 2 / 20 + age * .11;
        world.addParticle(
            dust,
            center.x + Math.cos(angle) * reach,
            center.y + .15 + Math.sin(age * .12) * .2,
            center.z + Math.sin(angle) * reach,
            0,
            .015,
            0);
      }
    }

    private void mushroom(ClientWorld world) {
      double height = Math.min(28, age * .45);
      double cap = Math.min(18, 1 + age * .23);
      for (int i = 0; i < 8; i++) {
        double angle = age * .35 + i * Math.PI / 4;
        double spread = cap * (.5 + world.random.nextDouble() * .5);
        world.addParticle(
            ParticleTypes.CAMPFIRE_COSY_SMOKE,
            center.x + Math.cos(angle) * spread,
            center.y + height,
            center.z + Math.sin(angle) * spread,
            Math.cos(angle) * .04,
            .08,
            Math.sin(angle) * .04);
        world.addParticle(
            ParticleTypes.CAMPFIRE_COSY_SMOKE,
            center.x + world.random.nextGaussian() * 1.2,
            center.y + height * world.random.nextDouble(),
            center.z + world.random.nextGaussian() * 1.2,
            0,
            .15,
            0);
        if (age < 30)
          world.addParticle(
              ParticleTypes.FLAME,
              center.x + Math.cos(angle) * cap,
              center.y + height,
              center.z + Math.sin(angle) * cap,
              0,
              .03,
              0);
      }
    }

    private void extra(ClientWorld world, PayloadVisuals.Profile profile) {
      ParticleEffect kind = particle(profile.particle());
      for (int i = 0; i < 6; i++) {
        double angle = world.random.nextDouble() * Math.PI * 2;
        double reach =
            switch (profile.shape()) {
              case VORTEX -> radius * (1 - age / 18.0);
              case COLUMN, SPARK -> Math.min(6, radius) * (.3 + world.random.nextDouble() * .7);
              default -> world.random.nextDouble() * Math.min(5, radius);
            };
        double y =
            switch (profile.shape()) {
              case COLUMN -> center.y + age * .45 + world.random.nextDouble();
              case SPARK -> center.y + world.random.nextDouble() * 4;
              case CLOUD -> center.y + age * .15;
              default -> center.y + world.random.nextDouble() * 2;
            };
        Vec3f motion = motion(profile.shape(), angle);
        var effect = profile.shape() == PayloadVisuals.Shape.PRISMATIC ? randomDust(world) : kind;
        world.addParticle(
            effect,
            center.x + Math.cos(angle) * reach,
            y,
            center.z + Math.sin(angle) * reach,
            motion.getX(),
            motion.getY(),
            motion.getZ());
      }
    }

    private Vec3f motion(PayloadVisuals.Shape shape, double angle) {
      return switch (shape) {
        case VORTEX -> new Vec3f((float) (-Math.cos(angle) * .12), .02f, (float) (-Math.sin(angle) * .12));
        case COLUMN -> new Vec3f(0, .12f, 0);
        case CLOUD -> new Vec3f(0, .04f, 0);
        case RING -> new Vec3f(0, 0, 0);
        default -> new Vec3f((float) (Math.cos(angle) * .08), .1f, (float) (Math.sin(angle) * .08));
      };
    }

    private DustParticleEffect randomDust(ClientWorld world) {
      return new DustParticleEffect(
          new Vec3f(world.random.nextFloat(), world.random.nextFloat(), world.random.nextFloat()), 1.6f);
    }
  }
}
