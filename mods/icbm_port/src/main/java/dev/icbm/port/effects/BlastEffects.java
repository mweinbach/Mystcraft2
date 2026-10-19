package dev.icbm.port.effects;

import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** One small presentation packet per accepted detonation, independent of terrain work budgets. */
public final class BlastEffects {
  public static final Identifier DETONATION = new Identifier("icbm_port", "detonation_effect");

  private BlastEffects() {}

  public static void broadcast(ServerWorld world, Vec3d position, PayloadType type, double radius) {
    for (var player : PlayerLookup.around(world, position, 192)) {
      if (!ServerPlayNetworking.canSend(player, DETONATION)) continue;
      var packet = PacketByteBufs.create();
      packet.writeEnumConstant(type);
      packet.writeDouble(position.x);
      packet.writeDouble(position.y);
      packet.writeDouble(position.z);
      packet.writeFloat((float) Math.max(1, Math.min(80, radius)));
      ServerPlayNetworking.send(player, DETONATION, packet);
    }
  }
}
