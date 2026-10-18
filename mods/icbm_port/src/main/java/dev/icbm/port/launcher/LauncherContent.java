package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
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
      LASER_DESIGNATOR = new TargetingItem(true),
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
        ScreenHandlerRegistry.registerSimple(IcbmPort.id("launcher"), LauncherScreenHandler::new);
    Registry.register(Registry.ITEM, IcbmPort.id("gps"), GPS);
    Registry.register(Registry.ITEM, IcbmPort.id("laser_designator"), LASER_DESIGNATOR);
    Registry.register(Registry.ITEM, IcbmPort.id("radio_detonator"), RADIO_DETONATOR);
    ServerPlayNetworking.registerGlobalReceiver(
        CONFIGURE,
        (server, player, handler, buf, response) -> {
          if (buf.readableBytes() != 25) return;
          int sync = buf.readInt();
          BlockPos target = buf.readBlockPos();
          boolean targeted = buf.readBoolean();
          int frequency = buf.readInt();
          int action = buf.readInt();
          int reserved = buf.readInt();
          server.execute(
              () -> {
                if (!validAction(action, reserved)
                    || !(player.currentScreenHandler instanceof LauncherScreenHandler screen)
                    || screen.syncId != sync
                    || !(screen.inventory instanceof LauncherBlockEntity launcher)
                    || !screen.canUse(player)
                    || player.isSpectator()) return;
                if (!launcher.configure(target, targeted, frequency)) return;
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

  @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
  public static void registerClient() {
    net.minecraft.client.gui.screen.ingame.HandledScreens.register(SCREEN, LauncherScreen::new);
  }

  public static boolean validTarget(World world, BlockPos launcher, BlockPos target) {
    int range =
        world.getBlockState(launcher).getBlock() instanceof dev.icbm.port.block.LauncherBlock block
            ? block.range()
            : dev.icbm.port.flight.FlightSettings.current.maxRange;
    return !world.isOutOfHeightLimit(target)
        && world.getWorldBorder().contains(target)
        && target.getSquaredDistance(launcher) <= (double) range * range;
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
