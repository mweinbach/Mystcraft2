package dev.icbm.port.client;

import dev.icbm.port.effects.BlastEffects;
import dev.icbm.port.payload.PayloadType;
import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/** Bounded, shader-independent shockwaves and rising blast plumes for every delivery form. */
public final class BlastEffectsClient {
  private static final ArrayDeque<Effect> ACTIVE = new ArrayDeque<>();
  private static ClientWorld lastWorld;

  private BlastEffectsClient() {}

  public static void register() {
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

  private static void sound(ClientWorld world, Vec3d p, PayloadType type) {
    var sound =
        switch (type) {
          case REDMATTER, ANTI_GRAVITATIONAL -> SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE;
          case EMP, SONIC -> SoundEvents.ENTITY_WARDEN_SONIC_BOOM;
          case ENDER -> SoundEvents.ENTITY_ENDERMAN_TELEPORT;
          case COLOR -> SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE;
          case EXOTHERMIC, INCENDIARY -> SoundEvents.ITEM_FIRECHARGE_USE;
          case ENDOTHERMIC -> SoundEvents.BLOCK_GLASS_BREAK;
          default -> SoundEvents.ENTITY_GENERIC_EXPLODE;
        };
    world.playSound(
        p.x,
        p.y,
        p.z,
        sound,
        SoundCategory.BLOCKS,
        type.tier() >= 3 ? 3f : 1.8f,
        type == PayloadType.REDMATTER ? .45f : .75f,
        false);
  }

  public static Vec3f color(PayloadType type) {
    return switch (type) {
      case REDMATTER, CONTAGIOUS -> new Vec3f(1f, .07f, .22f);
      case ANTIMATTER, ENDER -> new Vec3f(.72f, .16f, 1f);
      case EMP, SONIC, ANTI_GRAVITATIONAL -> new Vec3f(.12f, .8f, 1f);
      case NUCLEAR, CHEMICAL, DEBILITATION -> new Vec3f(.62f, 1f, .12f);
      case ENDOTHERMIC -> new Vec3f(.5f, .85f, 1f);
      case EXOTHERMIC, INCENDIARY, THERMOBARIC -> new Vec3f(1f, .35f, .04f);
      default -> new Vec3f(1f, .72f, .28f);
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
      dust = new DustParticleEffect(color(type), type.tier() >= 3 ? 2.5f : 1.5f);
    }

    private boolean tick(ClientWorld world) {
      if (++age > (type == PayloadType.NUCLEAR ? 100 : 40)) return true;
      if (age <= 22) {
        double ring = radius * age / 22;
        for (int i = 0; i < 20; i++) {
          double a = i * Math.PI * 2 / 20 + age * .11;
          world.addParticle(
              dust,
              center.x + Math.cos(a) * ring,
              center.y + .15 + Math.sin(age * .12) * .2,
              center.z + Math.sin(a) * ring,
              0,
              .015,
              0);
        }
      }
      if (type == PayloadType.NUCLEAR) {
        double height = Math.min(28, age * .45);
        double cap = Math.min(18, 1 + age * .23);
        // Rising stem, expanding mushroom cap and an illuminated rim.
        for (int i = 0; i < 8; i++) {
          double a = age * .35 + i * Math.PI / 4;
          double r = cap * (.5 + world.random.nextDouble() * .5);
          world.addParticle(
              ParticleTypes.CAMPFIRE_COSY_SMOKE,
              center.x + Math.cos(a) * r,
              center.y + height,
              center.z + Math.sin(a) * r,
              Math.cos(a) * .04,
              .08,
              Math.sin(a) * .04);
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
                center.x + Math.cos(a) * cap,
                center.y + height,
                center.z + Math.sin(a) * cap,
                0,
                .03,
                0);
        }
      } else if (age <= 12) {
        for (int i = 0; i < 6; i++) {
          double a = world.random.nextDouble() * Math.PI * 2;
          double r = world.random.nextDouble() * Math.min(5, radius);
          var particle =
              switch (type) {
                case ENDOTHERMIC -> ParticleTypes.SNOWFLAKE;
                case REDMATTER, ENDER, ANTIMATTER -> ParticleTypes.REVERSE_PORTAL;
                case EMP, SONIC, ANTI_GRAVITATIONAL -> ParticleTypes.ELECTRIC_SPARK;
                case CHEMICAL, DEBILITATION, CONTAGIOUS, SMOKE -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
                default -> ParticleTypes.FLAME;
              };
          world.addParticle(
              particle,
              center.x + Math.cos(a) * r,
              center.y + world.random.nextDouble() * 2,
              center.z + Math.sin(a) * r,
              Math.cos(a) * .08,
              .1,
              Math.sin(a) * .08);
        }
      }
      return false;
    }
  }
}
