package dev.icbm.port.command;

import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import dev.icbm.port.api.*;
import dev.icbm.port.payload.Payloads;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;

/** Modern Brigadier adaptation of upstream ICBMCommands, CommandBlast*, Remove and Lag. */
public final class IcbmCommands {
  public static final int MAX_RADIUS = 1024, MAX_GRID_COUNT = 4, MAX_SPACING = 128;
  /** Antimatter and red matter accept up to 384; other families still reject sizes above 80. */
  public static final double MAX_SIZE = dev.icbm.port.payload.PayloadType.MAX_EXTREME_RADIUS;
  private static final SimpleCommandExceptionType INVALID =
      new SimpleCommandExceptionType(
          Text.literal("Unknown ICBM action/type, invalid location, or unloaded target"));

  private IcbmCommands() {}

  public static void register() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registry, environment) -> register(dispatcher));
  }

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    var root =
        literal("icbm")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(c -> help(c.getSource()));
    root.then(literal("help").executes(c -> help(c.getSource())));
    var blast = literal("blast");
    blast.then(
        literal("list")
            .executes(
                c -> {
                  c.getSource()
                      .sendFeedback(
                          Text.literal(
                              "Explosive Types: "
                                  + IcbmApi.actions().stream()
                                      .map(d -> d.id().toString())
                                      .sorted()
                                      .collect(java.util.stream.Collectors.joining(", "))),
                          false);
                  return IcbmApi.actions().size();
                }));
    var trigger =
        literal("trigger")
            .then(
                argument("id", IdentifierArgumentType.identifier())
                    .suggests(
                        (c, b) ->
                            CommandSource.suggestMatching(
                                IcbmApi.actions().stream().map(d -> d.id().toString()), b))
                    .then(
                        argument("size", DoubleArgumentType.doubleArg(.01, MAX_SIZE))
                            .executes(
                                c ->
                                    trigger(
                                        c, c.getSource().getWorld(), c.getSource().getPosition())))
                    .then(
                        argument("dimension", DimensionArgumentType.dimension())
                            .then(
                                argument("position", Vec3ArgumentType.vec3())
                                    .then(
                                        argument("size", DoubleArgumentType.doubleArg(.01, MAX_SIZE))
                                            .executes(
                                                c ->
                                                    trigger(
                                                        c,
                                                        DimensionArgumentType.getDimensionArgument(
                                                            c, "dimension"),
                                                        Vec3ArgumentType.getVec3(
                                                            c, "position")))))));
    blast.then(trigger);
    blast.then(
        literal("spread")
            .then(
                argument("count", IntegerArgumentType.integer(1, MAX_GRID_COUNT))
                    .then(
                        argument("distance", IntegerArgumentType.integer(1, MAX_SPACING))
                            .then(
                                argument("id", IdentifierArgumentType.identifier())
                                    .suggests(
                                        (c, b) ->
                                            CommandSource.suggestMatching(
                                                IcbmApi.actions().stream()
                                                    .map(d -> d.id().toString()),
                                                b))
                                    .then(
                                        argument("dimension", DimensionArgumentType.dimension())
                                            .then(
                                                argument("position", Vec3ArgumentType.vec3())
                                                    .then(
                                                        argument(
                                                                "size",
                                                                DoubleArgumentType.doubleArg(
                                                                    .01, MAX_SIZE))
                                                            .executes(IcbmCommands::spread))))))));
    root.then(blast);
    root.then(
        literal("remove")
            .then(
                argument("type", StringArgumentType.word())
                    .suggests(
                        (c, b) ->
                            CommandSource.suggestMatching(
                                List.of("all", "missiles", "explosions"), b))
                    .executes(
                        c ->
                            remove(
                                c,
                                c.getSource().getWorld(),
                                c.getSource().getPosition(),
                                1000,
                                false))
                    .then(
                        argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                            .executes(
                                c ->
                                    remove(
                                        c,
                                        c.getSource().getWorld(),
                                        c.getSource().getPosition(),
                                        IntegerArgumentType.getInteger(c, "radius"),
                                        false)))
                    .then(
                        argument("dimension", DimensionArgumentType.dimension())
                            .then(
                                argument("position", Vec3ArgumentType.vec3())
                                    .then(
                                        argument(
                                                "radius",
                                                IntegerArgumentType.integer(1, MAX_RADIUS))
                                            .executes(
                                                c ->
                                                    remove(
                                                        c,
                                                        DimensionArgumentType.getDimensionArgument(
                                                            c, "dimension"),
                                                        Vec3ArgumentType.getVec3(c, "position"),
                                                        IntegerArgumentType.getInteger(c, "radius"),
                                                        false)))))));
    root.then(
        literal("lag")
            .executes(
                c -> remove(c, c.getSource().getWorld(), c.getSource().getPosition(), 1000, true))
            .then(
                argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                    .executes(
                        c ->
                            remove(
                                c,
                                c.getSource().getWorld(),
                                c.getSource().getPosition(),
                                IntegerArgumentType.getInteger(c, "radius"),
                                true))));
    dispatcher.register(root);
  }

  private static Identifier action(String input) throws CommandSyntaxException {
    Identifier id =
        Identifier.tryParse(
            input.contains(":") ? input : "icbm_port:" + input.toLowerCase(Locale.ROOT));
    if (id != null
        && (id.getNamespace().equals("minecraft") || id.getNamespace().equals("icbmclassic"))) {
      String path = id.getPath();
      String[] legacy = {
        "condensed",
        "shrapnel",
        "incendiary",
        "debilitation",
        "chemical",
        "anvil",
        "repulsive",
        "attractive",
        "fragmentation",
        "contagious",
        "sonic",
        "breaching",
        null,
        "thermobaric",
        null,
        "nuclear",
        "emp",
        "exothermic",
        "endothermic",
        "antigravitational",
        "ender",
        "hypersonic",
        "antimatter",
        "redmatter",
        "missile",
        "rot",
        "mutation"
      };
      try {
        int number = Integer.parseInt(path);
        if (number >= 0 && number < legacy.length && legacy[number] != null) path = legacy[number];
      } catch (NumberFormatException ignored) {
      }
      id = new Identifier("icbm_port", path);
    }
    if (id != null && id.getNamespace().equals("minecraft") && IcbmApi.find(id) == null)
      id = new Identifier("icbm_port", id.getPath());
    if (id == null || IcbmApi.find(id) == null) throw INVALID.create();
    return id;
  }

  private static void validate(ServerWorld world, Vec3d pos) throws CommandSyntaxException {
    if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z))
      throw INVALID.create();
    BlockPos block = new BlockPos(pos);
    if (world.isOutOfHeightLimit(block)
        || !world.getWorldBorder().contains(block)
        || !world.isChunkLoaded(block)) throw INVALID.create();
  }

  private static int trigger(CommandContext<ServerCommandSource> c, ServerWorld world, Vec3d pos)
      throws CommandSyntaxException {
    validate(world, pos);
    Identifier id = action(IdentifierArgumentType.getIdentifier(c, "id").toString());
    boolean accepted =
        IcbmApi.execute(
            new ActionContext(
                world,
                pos,
                id,
                DoubleArgumentType.getDouble(c, "size"),
                ActionSource.command(c.getSource().getEntity(), new BlockPos(pos))));
    c.getSource()
        .sendFeedback(
            Text.literal(id + ": " + (accepted ? "accepted" : "blocked or unavailable")), true);
    return accepted ? 1 : 0;
  }

  private static int spread(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
    ServerWorld world = DimensionArgumentType.getDimensionArgument(c, "dimension");
    Vec3d center = Vec3ArgumentType.getVec3(c, "position");
    int count = IntegerArgumentType.getInteger(c, "count"),
        distance = IntegerArgumentType.getInteger(c, "distance");
    // Validate the whole grid before any side effects; no implicit chunk loading.
    for (int x = -count; x <= count; x++)
      for (int z = -count; z <= count; z++)
        validate(world, center.add(x * distance, 0, z * distance));
    Identifier id = action(IdentifierArgumentType.getIdentifier(c, "id").toString());
    double size = DoubleArgumentType.getDouble(c, "size");
    int accepted = 0;
    for (int x = -count; x <= count; x++)
      for (int z = -count; z <= count; z++) {
        Vec3d pos = center.add(x * distance, 0, z * distance);
        if (IcbmApi.execute(
            new ActionContext(
                world,
                pos,
                id,
                size,
                ActionSource.command(c.getSource().getEntity(), new BlockPos(pos))))) accepted++;
      }
    c.getSource()
        .sendFeedback(
            Text.literal(
                "Spread accepted "
                    + accepted
                    + " / "
                    + ((count * 2 + 1) * (count * 2 + 1))
                    + " actions"),
            true);
    return accepted;
  }

  public static boolean matches(Entity entity, String type) {
    if (entity.isRemoved()
        || !Registry.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("icbm_port"))
      return false;
    String path = Registry.ENTITY_TYPE.getId(entity.getType()).getPath();
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "all" -> true;
      case "missile", "missiles" ->
          entity instanceof dev.icbm.port.entity.MissileEntity
              || entity instanceof dev.icbm.port.delivery.HeldItemMissileEntity
              || path.equals("sam_interceptor");
      case "ex", "explosive", "explosives", "explosion", "explosions" ->
          entity instanceof dev.icbm.port.entity.PrimedExplosiveEntity;
      default -> false;
    };
  }

  private static int remove(
      CommandContext<ServerCommandSource> c, ServerWorld world, Vec3d pos, int radius, boolean lag)
      throws CommandSyntaxException {
    String type = lag ? "all" : StringArgumentType.getString(c, "type");
    if (!List.of(
            "all",
            "missile",
            "missiles",
            "ex",
            "explosive",
            "explosives",
            "explosion",
            "explosions")
        .contains(type.toLowerCase(Locale.ROOT))) throw INVALID.create();
    if (!Double.isFinite(pos.x + pos.y + pos.z)
        || !world.getWorldBorder().contains(new BlockPos(pos))) throw INVALID.create();
    List<Entity> selected = new ArrayList<>();
    for (Entity entity : world.iterateEntities())
      if (matches(entity, type) && entity.squaredDistanceTo(pos) <= radius * (double) radius)
        selected.add(entity);
    // Upstream lag/remove intentionally cancel effects rather than detonate or recover ammunition.
    selected.forEach(Entity::discard);
    int effects =
        lag
            ? Payloads.removeNear(world, pos, radius)
                + dev.icbm.port.flight.FlightLedger.get(world).removeNear(world, pos, radius)
            : 0;
    c.getSource()
        .sendFeedback(
            Text.literal(
                "Removed "
                    + selected.size()
                    + " ICBM entities and "
                    + effects
                    + " queued effects within "
                    + radius),
            true);
    return selected.size() + effects;
  }

  private static int help(ServerCommandSource source) {
    source.sendFeedback(
        Text.literal(
            "ICBM (permission 2): blast list; blast trigger <id> <size> or <id> <dimension> <x y z>"
                + " <size>; blast spread <count 1-4> <spacing 1-128> <id> <dimension> <x y z> <size"
                + " .01-384 (above 80: antimatter and red matter only)>; remove"
                + " <all|missiles|explosions> [radius 1-1024] or <type> <dimension>"
                + " <x y z> <radius>; lag [radius]; help. Locations support relative coordinates."
                + " Loaded terrain only."),
        false);
    return 1;
  }
}
