package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.launcher.LauncherBlockEntity;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Upstream radar gun stores a traced GPS point, rather than scanning entities.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/items/ItemRadarGun.java}: right-click records the
 * exact point under the crosshair within {@link #MAX_RANGE} blocks together with its dimension,
 * using it directly on a block records the clicked point, sneak-use clears it, and the tooltip
 * shows the stored coordinates. Upstream launchers read the GPS item on activation; here using the
 * gun on a launcher copies the stored target into it (Ender payloads take it as their teleport
 * destination), matching how the port's designator items configure launchers.
 */
public final class RadarGunItem extends Item {
  public static final double MAX_RANGE = 200;
  public static final String NBT_KEY = "IcbmTarget";

  RadarGunItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  /** Stores the precise hit point; the block position stays readable by launcher helpers. */
  public static void record(World world, ItemStack stack, Vec3d hit) {
    NbtCompound target = stack.getOrCreateSubNbt(NBT_KEY);
    target.putLong("Position", new BlockPos(hit).asLong());
    target.putDouble("X", hit.x);
    target.putDouble("Y", hit.y);
    target.putDouble("Z", hit.z);
    target.putString("Dimension", world.getRegistryKey().getValue().toString());
  }

  public static BlockPos storedPosition(ItemStack stack) {
    NbtCompound target = stack.getSubNbt(NBT_KEY);
    return target != null && target.contains("Position")
        ? BlockPos.fromLong(target.getLong("Position"))
        : null;
  }

  public static Identifier storedDimension(ItemStack stack) {
    NbtCompound target = stack.getSubNbt(NBT_KEY);
    return target == null ? null : Identifier.tryParse(target.getString("Dimension"));
  }

  private static void clear(PlayerEntity player, ItemStack stack) {
    stack.removeSubNbt(NBT_KEY);
    player.sendMessage(Text.translatable("message.icbm_port.radar_gun.cleared"), true);
  }

  private static String format(Vec3d pos) {
    return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", pos.x, pos.y, pos.z);
  }

  /**
   * Copies the stored point into a launcher. Ender payloads accept any dimension as their
   * teleport destination; other payloads need a same-dimension target the launcher accepts.
   */
  public static boolean copyToLauncher(World world, ItemStack stack, LauncherBlockEntity launcher) {
    BlockPos target = storedPosition(stack);
    Identifier dimension = storedDimension(stack);
    if (target == null || dimension == null) return false;
    boolean ender =
        IcbmPort.payloadForMissile(launcher.getStack(0))
            == dev.icbm.port.payload.PayloadType.ENDER;
    return ender
        ? launcher.configurePayloadDestination(
            RegistryKey.of(Registry.WORLD_KEY, dimension), target)
        : world.getRegistryKey().getValue().equals(dimension)
            && launcher.configure(target, true, launcher.frequency());
  }

  @Override
  public ActionResult useOnBlock(ItemUsageContext context) {
    World world = context.getWorld();
    PlayerEntity player = context.getPlayer();
    if (player == null) return ActionResult.PASS;
    ItemStack stack = context.getStack();
    if (!world.isClient) {
      if (player.isSneaking()) clear(player, stack);
      else if (world.getBlockEntity(context.getBlockPos()) instanceof LauncherBlockEntity launcher) {
        boolean ok = copyToLauncher(world, stack, launcher);
        player.sendMessage(
            ok
                ? Text.translatable(
                    "message.icbm_port.radar_gun.copied", storedPosition(stack).toShortString())
                : Text.translatable("message.icbm_port.radar_gun.invalid"),
            true);
      } else {
        record(world, stack, context.getHitPos());
        player.sendMessage(
            Text.translatable("message.icbm_port.radar_gun.set", format(context.getHitPos())),
            true);
      }
    }
    return ActionResult.success(world.isClient);
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (!world.isClient) {
      if (player.isSneaking()) clear(player, stack);
      else {
        HitResult hit = player.raycast(MAX_RANGE, 1, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
          record(world, stack, hit.getPos());
          player.sendMessage(
              Text.translatable("message.icbm_port.radar_gun.set", format(hit.getPos())), true);
        } else
          player.sendMessage(
              Text.translatable("message.icbm_port.radar_gun.out_of_range", (int) MAX_RANGE),
              true);
      }
    }
    return TypedActionResult.success(stack, world.isClient);
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    NbtCompound target = stack.getSubNbt(NBT_KEY);
    if (target != null && target.contains("Position")) {
      Vec3d pos =
          target.contains("X")
              ? new Vec3d(target.getDouble("X"), target.getDouble("Y"), target.getDouble("Z"))
              : Vec3d.of(BlockPos.fromLong(target.getLong("Position")));
      tooltip.add(
          Text.translatable(
              "tooltip.icbm_port.radar_gun.stored", format(pos), target.getString("Dimension")));
    } else tooltip.add(Text.translatable("tooltip.icbm_port.radar_gun.empty"));
    tooltip.add(Text.translatable("tooltip.icbm_port.radar_gun.info", (int) MAX_RANGE));
  }
}
