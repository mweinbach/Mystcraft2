package dev.icbm.port.simulation.client;

import dev.icbm.port.simulation.*;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.math.random.Random;

public final class SimulationClient {
  private static final Map<Integer, CoreSound> SOUNDS = new HashMap<>();
  private static final DustParticleEffect RED_DUST =
      new DustParticleEffect(new Vec3f(1, .06f, .16f), 1.4f);
  private static ClientWorld lastWorld;

  private SimulationClient() {}

  public static void register() {
    EntityRendererRegistry.register(SimulationContent.FLYING_BLOCK, FlyingBlockRenderer::new);
    EntityRendererRegistry.register(SimulationContent.REDMATTER, RedmatterRenderer::new);
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (client.world != lastWorld) {
            SOUNDS.values().forEach(sound -> client.getSoundManager().stop(sound));
            SOUNDS.clear();
            lastWorld = client.world;
          }
          SOUNDS.values().removeIf(sound -> sound.core.isRemoved());
          if (client.world == null || client.player == null) return;
          int visible = 0;
          for (var entity : client.world.getEntities()) {
            if (!(entity instanceof RedmatterEntity core)
                || core.squaredDistanceTo(client.player) > 96 * 96
                || visible++ >= 8) continue;
            SOUNDS.computeIfAbsent(
                core.getId(),
                id -> {
                  CoreSound sound = new CoreSound(core);
                  client.getSoundManager().play(sound);
                  return sound;
                });
            if (core.age % 2 != 0) continue;
            double radius = Math.max(1.2, core.size() * .9);
            for (int i = 0; i < 6; i++) {
              double angle = core.age * .14 + i * Math.PI / 3;
              double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
              client.world.addParticle(
                  RED_DUST,
                  core.getX() + x,
                  core.getY() + Math.sin(angle * 2 + core.age * .04) * radius * .35,
                  core.getZ() + z,
                  -x * .08 - z * .1,
                  0,
                  -z * .08 + x * .1);
            }
          }
        });
  }

  private static final class CoreSound extends MovingSoundInstance {
    private final RedmatterEntity core;

    CoreSound(RedmatterEntity core) {
      super(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, Random.create());
      this.core = core;
      repeat = true;
      repeatDelay = 0;
      volume = .65f;
      pitch = .45f;
      x = core.getX();
      y = core.getY();
      z = core.getZ();
    }

    @Override
    public void tick() {
      if (core.isRemoved() || MinecraftClient.getInstance().world != core.world) {
        setDone();
        return;
      }
      x = core.getX();
      y = core.getY();
      z = core.getZ();
      pitch = .45f + Math.min(.5f, core.size() * .025f);
    }
  }

  private static final class FlyingBlockRenderer extends EntityRenderer<FlyingBlockEntity> {
    private final net.minecraft.client.render.block.BlockRenderManager blocks;

    FlyingBlockRenderer(EntityRendererFactory.Context context) {
      super(context);
      blocks = context.getBlockRenderManager();
      shadowRadius = .5f;
    }

    @Override
    public void render(
        FlyingBlockEntity entity,
        float yaw,
        float delta,
        MatrixStack matrices,
        VertexConsumerProvider vertices,
        int light) {
      matrices.push();
      matrices.translate(0, .5, 0);
      matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((entity.age + delta) * 2));
      matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion((entity.age + delta) * 1.3f));
      matrices.translate(-.5, -.5, -.5);
      blocks.renderBlockAsEntity(
          entity.blockState(), matrices, vertices, light, OverlayTexture.DEFAULT_UV);
      matrices.pop();
      super.render(entity, yaw, delta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(FlyingBlockEntity entity) {
      return net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
  }
}
