package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public final class LauncherContent {
  public static final Identifier CONFIGURE = IcbmPort.id("launcher_configure");

  /**
   * Launcher screen packet: sync id (4), target (8), arc flag (1), channel (4), action (4), reserved
   * (4), firing delay (4) and lock height (4).
   */
  public static final int CONFIGURE_PACKET_BYTES = 33;
  public static final dev.icbm.port.block.LauncherBlock BASE_TIER_1 = base(1, false),
      BASE_TIER_2 = base(2, false),
      BASE_TIER_3 = base(3, false),
      CRUISE = base(1, true);
  public static final LauncherComponentBlock FRAME = component(LauncherComponentBlock.Role.FRAME),
      SUPPORT = component(LauncherComponentBlock.Role.SUPPORT),
      SCREEN_BLOCK = component(LauncherComponentBlock.Role.SCREEN),
      CONNECTOR = component(LauncherComponentBlock.Role.CONNECTOR);

  private static net.minecraft.block.AbstractBlock.Settings settings() {
    return net.minecraft.block.AbstractBlock.Settings.of(net.minecraft.block.Material.METAL)
        .strength(3.5F)
        .requiresTool();
  }

  private static dev.icbm.port.block.LauncherBlock base(int tier, boolean cruise) {
    return new dev.icbm.port.block.LauncherBlock(settings(), tier, cruise);
  }

  private static LauncherComponentBlock component(LauncherComponentBlock.Role role) {
    return new LauncherComponentBlock(settings(), role);
  }

  private static void block(String id, net.minecraft.block.Block block) {
    Registry.register(Registry.BLOCK, IcbmPort.id(id), block);
    Registry.register(
        Registry.ITEM,
        IcbmPort.id(id),
        new net.minecraft.item.BlockItem(block, new Item.Settings().group(IcbmPort.GROUP)));
  }

  public static BlockEntityType<LauncherBlockEntity> BLOCK_ENTITY;
  public static ScreenHandlerType<LauncherScreenHandler> SCREEN;
  public static final Item GPS = new TargetingItem(false),
      LASER_DESIGNATOR = new LaserDetonatorItem(),
      RADIO_DETONATOR = new RadioItem();

  public static void register() {
    dev.icbm.port.flight.FlightSettings.load();
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(
        world -> dev.icbm.port.flight.FlightLedger.get(world).tick(world));
    block("launcher_base_tier_1", BASE_TIER_1);
    block("launcher_base_tier_2", BASE_TIER_2);
    block("launcher_base_tier_3", BASE_TIER_3);
    block("cruise_launcher", CRUISE);
    block("launcher_frame", FRAME);
    block("launcher_support", SUPPORT);
    block("launcher_screen", SCREEN_BLOCK);
    block("launcher_connector", CONNECTOR);
    BLOCK_ENTITY =
        Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            IcbmPort.id("launcher"),
            FabricBlockEntityTypeBuilder.create(
                    LauncherBlockEntity::new,
                    IcbmPort.LAUNCHER,
                    BASE_TIER_1,
                    BASE_TIER_2,
                    BASE_TIER_3,
                    CRUISE)
                .build());
    SCREEN =
        Registry.register(
            Registry.SCREEN_HANDLER,
            IcbmPort.id("launcher"),
            new ScreenHandlerType<>(LauncherScreenHandler::new));
    Registry.register(Registry.ITEM, IcbmPort.id("gps"), GPS);
    Registry.register(Registry.ITEM, IcbmPort.id("laser_designator"), LASER_DESIGNATOR);
    Registry.register(Registry.ITEM, IcbmPort.id("radio_detonator"), RADIO_DETONATOR);
    dev.icbm.port.radio.RadioNetwork.registerLifecycle();
    // Upstream LauncherNetwork capabilities, reached through frames, connectors and screens.
    team.reborn.energy.api.EnergyStorage.SIDED.registerForBlocks(
        (world, pos, state, entity, side) -> LauncherNetworkStorage.energy(world, pos),
        FRAME,
        SUPPORT,
        SCREEN_BLOCK,
        CONNECTOR);
    net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlocks(
        (world, pos, state, entity, side) -> LauncherNetworkStorage.items(world, pos),
        FRAME,
        SUPPORT,
        SCREEN_BLOCK,
        CONNECTOR);
    ServerPlayNetworking.registerGlobalReceiver(
        CONFIGURE,
        (server, player, handler, buf, response) -> {
          if (buf.readableBytes() != CONFIGURE_PACKET_BYTES) return;
          int sync = buf.readInt();
          BlockPos target = buf.readBlockPos();
          boolean targeted = buf.readBoolean();
          int frequency = buf.readInt();
          int action = buf.readInt();
          int reserved = buf.readInt();
          int delay = buf.readInt();
          int lockHeight = buf.readInt();
          server.execute(
              () -> {
                if (!validAction(action, reserved)
                    || !validLaunchSettings(delay, lockHeight)
                    || !(player.currentScreenHandler instanceof LauncherScreenHandler screen)
                    || screen.syncId != sync
                    || !(screen.inventory instanceof LauncherBlockEntity launcher)
                    || launcher.isRemoved()
                    || launcher.getWorld() != player.world
                    || !player.world.isChunkLoaded(launcher.getPos())
                    || !screen.canUse(player)
                    || player.isSpectator()) return;
                // Validate everything before mutating anything.
                if (!validTarget(player.world, launcher.getPos(), target)
                    || frequency < 0
                    || frequency > dev.icbm.port.radio.RadioNetwork.MAX_CHANNEL) return;
                if (!launcher.configure(target, targeted, frequency)
                    || !launcher.configureLaunch(delay, lockHeight)) return;
                if (action == 1) launcher.charge();
                if (action == 2)
                  launcher
                      .launcherBlock()
                      .launch(
                          (net.minecraft.server.world.ServerWorld) player.world, launcher.getPos());
                screen.sendContentUpdates();
              });
        });
  }

  public static boolean validAction(int action, int reserved) {
    return reserved == 0 && action >= 0 && action <= 2;
  }

  /** Firing delay 0..1200 ticks and lock height 3..64 blocks; anything else fails closed. */
  public static boolean validLaunchSettings(int delay, int lockHeight) {
    return delay == LauncherRules.clampFiringDelay(delay)
        && lockHeight == LauncherRules.clampLockHeight(lockHeight);
  }

  @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
  public static void registerClient() {
    net.minecraft.client.gui.screen.ingame.HandledScreens.register(SCREEN, LauncherScreen::new);
  }

  /**
   * A storable target: inside the build height and world border and within the launcher's range on
   * both X and Z (upstream {@code LauncherCapability#isTargetTooFar} is per axis, a square).
   */
  public static boolean validTarget(World world, BlockPos launcher, BlockPos target) {
    int range =
        world.getBlockState(launcher).getBlock() instanceof dev.icbm.port.block.LauncherBlock block
            ? block.range()
            : dev.icbm.port.flight.FlightSettings.current.maxRange;
    return !world.isOutOfHeightLimit(target)
        && world.getWorldBorder().contains(target)
        && !LauncherRules.tooFar(
            launcher.getX() + .5, launcher.getZ() + .5, target.getX() + .5, target.getZ() + .5, range);
  }

  public static void writeTarget(World world, ItemStack stack, BlockPos target) {
    NbtCompound nbt = stack.getOrCreateSubNbt("IcbmTarget");
    nbt.putLong("Position", target.asLong());
    nbt.putString("Dimension", world.getRegistryKey().getValue().toString());
  }

  public static BlockPos readTarget(World world, ItemStack stack) {
    NbtCompound nbt = stack.getSubNbt("IcbmTarget");
    return nbt != null
            && nbt.contains("Position")
            && world.getRegistryKey().getValue().toString().equals(nbt.getString("Dimension"))
        ? BlockPos.fromLong(nbt.getLong("Position"))
        : null;
  }
}
