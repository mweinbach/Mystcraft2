package dev.icbm.port.defense;

import net.fabricmc.api.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;

/**
 * Moving hardware and emissive status are driven exclusively by server-synchronized machine flags.
 */
@Environment(EnvType.CLIENT)
public final class DefenseMachineRenderer
    implements BlockEntityRenderer<DefenseMachineBlockEntity> {
  public DefenseMachineRenderer(BlockEntityRendererFactory.Context context) {}

  @Override
  public boolean rendersOutsideBoundingBox(DefenseMachineBlockEntity machine) {
    return true;
  }

  @Override
  public int getRenderDistance() {
    return 48;
  }

  @Override
  public void render(
      DefenseMachineBlockEntity machine,
      float tickDelta,
      MatrixStack matrices,
      VertexConsumerProvider buffers,
      int light,
      int overlay) {
    if (machine.getWorld() == null) return;
    int flags = machine.visualFlags();
    boolean ready = (flags & 1) != 0, disabled = (flags & 8) != 0;
    float time = machine.getWorld().getTime() + tickDelta;
    String status = disabled ? "warning" : ready ? "optic" : "hazard";
    box(
        matrices,
        buffers,
        .17f,
        .32f,
        -.012f,
        .18f,
        .075f,
        .025f,
        status,
        ready || disabled ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light,
        overlay);
    switch (machine.kind()) {
      case RADAR -> radar(matrices, buffers, time, ready, (flags & 4) != 0, light, overlay);
      case EMP -> emp(matrices, buffers, time, ready, (flags & 2) != 0, light, overlay);
      case SAM ->
          sam(
              matrices,
              buffers,
              time,
              ready,
              (flags & 16) != 0,
              machine.visualYaw(),
              light,
              overlay);
    }
  }

  private void radar(
      MatrixStack m,
      VertexConsumerProvider b,
      float time,
      boolean scanning,
      boolean detected,
      int light,
      int overlay) {
    m.push();
    m.translate(.5, 1.08, .5);
    m.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(scanning ? time * 2.4f : 0));
    box(m, b, -.055f, -.09f, -.055f, .11f, .3f, .11f, "carbon", light, overlay);
    m.translate(0, .23, 0);
    m.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-30));
    // Five facets form a curved reflector, with a central receiver on its boom.
    for (int panel = -2; panel <= 2; panel++) {
      m.push();
      m.translate(panel * .13, 0, Math.abs(panel) * .035);
      m.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(-panel * 13));
      float height = panel == 0 ? .42f : Math.abs(panel) == 1 ? .36f : .24f;
      box(m, b, -.07f, -height / 2, 0, .14f, height, .035f, "alloy", light, overlay);
      m.pop();
    }
    box(m, b, -.018f, -.018f, -.32f, .036f, .036f, .34f, "carbon", light, overlay);
    box(
        m,
        b,
        -.055f,
        -.055f,
        -.37f,
        .11f,
        .11f,
        .07f,
        detected ? "warning" : "optic",
        scanning ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light,
        overlay);
    m.pop();
    if (scanning) {
      m.push();
      m.translate(.5, .34, .5);
      m.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(time * 4.8f));
      box(
          m,
          b,
          -.014f,
          0,
          -.41f,
          .028f,
          .014f,
          .41f,
          "optic",
          LightmapTextureManager.MAX_LIGHT_COORDINATE,
          overlay);
      m.pop();
    }
  }

  private void emp(
      MatrixStack m,
      VertexConsumerProvider b,
      float time,
      boolean ready,
      boolean cooling,
      int light,
      int overlay) {
    boolean active = ready || cooling;
    float phase = active ? (time % 40) / 40f : 0;
    for (int ring = 0; ring < 2; ring++) {
      float y =
          .39f
              + ring * .3f
              + (active ? (float) Math.sin((phase + ring * .5) * Math.PI * 2) * .075f : 0);
      for (int part = 0; part < 12; part++) {
        m.push();
        m.translate(.5, y, .5);
        m.multiply(
            Vec3f.POSITIVE_Y.getDegreesQuaternion(
                part * 30 + (active ? time * (cooling ? -2 : 1.2f) : 0)));
        box(
            m,
            b,
            -.075f,
            0,
            -.43f,
            .15f,
            .025f,
            .035f,
            active ? "optic" : "coil",
            active ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light,
            overlay);
        m.pop();
      }
    }
    if (cooling) {
      float radius = .32f + phase * .35f;
      for (int part = 0; part < 12; part++) {
        m.push();
        m.translate(.5, 1.04 + phase * .35, .5);
        m.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(part * 30));
        box(
            m,
            b,
            -.04f,
            0,
            -radius,
            .08f,
            .018f,
            .024f,
            "optic",
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            overlay);
        m.pop();
      }
    }
  }

  private void sam(
      MatrixStack m,
      VertexConsumerProvider b,
      float time,
      boolean armed,
      boolean tracking,
      float targetYaw,
      int light,
      int overlay) {
    m.push();
    m.translate(.5, 1.05, .5);
    m.multiply(
        Vec3f.POSITIVE_Y.getDegreesQuaternion(
            tracking ? targetYaw : armed ? (float) Math.sin(time * .018) * 55 : 0));
    box(m, b, -.22f, 0, -.16f, .44f, .12f, .32f, "carbon", light, overlay);
    m.translate(0, .11, 0);
    m.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(tracking ? -25 : -10));
    for (int side = -1; side <= 1; side += 2) {
      box(m, b, side * .17f - .065f, 0, -.3f, .13f, .13f, .52f, "alloy", light, overlay);
      box(
          m,
          b,
          side * .17f - .048f,
          .016f,
          -.315f,
          .096f,
          .096f,
          .025f,
          armed ? "optic" : "carbon",
          armed ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light,
          overlay);
    }
    m.pop();
  }

  private static void box(
      MatrixStack matrices,
      VertexConsumerProvider buffers,
      float x,
      float y,
      float z,
      float width,
      float height,
      float depth,
      String texture,
      int light,
      int overlay) {
    Sprite sprite =
        MinecraftClient.getInstance()
            .getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
            .apply(new Identifier("icbm_port", "block/" + texture));
    VertexConsumer vertices = buffers.getBuffer(RenderLayer.getSolid());
    matrices.push();
    matrices.translate(x, y, z);
    matrices.scale(width, height, depth);
    var entry = matrices.peek();
    face(
        vertices,
        entry,
        sprite,
        light,
        overlay,
        0,
        0,
        -1,
        new float[][] {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}});
    face(
        vertices,
        entry,
        sprite,
        light,
        overlay,
        0,
        0,
        1,
        new float[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}});
    face(
        vertices,
        entry,
        sprite,
        light,
        overlay,
        -1,
        0,
        0,
        new float[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}});
    face(
        vertices,
        entry,
        sprite,
        light,
        overlay,
        1,
        0,
        0,
        new float[][] {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}});
    face(
        vertices,
        entry,
        sprite,
        light,
        overlay,
        0,
        1,
        0,
        new float[][] {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}});
    face(
        vertices,
        entry,
        sprite,
        light,
        overlay,
        0,
        -1,
        0,
        new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}});
    matrices.pop();
  }

  private static void face(
      VertexConsumer consumer,
      MatrixStack.Entry entry,
      Sprite sprite,
      int light,
      int overlay,
      float nx,
      float ny,
      float nz,
      float[][] points) {
    for (int index = 0; index < 4; index++) {
      float[] point = points[index];
      consumer
          .vertex(entry.getPositionMatrix(), point[0], point[1], point[2])
          .color(255, 255, 255, 255)
          .texture(
              index == 0 || index == 3 ? sprite.getMinU() : sprite.getMaxU(),
              index < 2 ? sprite.getMaxV() : sprite.getMinV())
          .overlay(overlay)
          .light(light)
          .normal(entry.getNormalMatrix(), nx, ny, nz)
          .next();
    }
  }
}
