package dev.icbm.port.api;

import dev.icbm.port.payload.PayloadType;
import dev.icbm.port.payload.Payloads;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Fabric extension surface; registrations are explicit and duplicates are rejected. */
public final class IcbmApi {
  @FunctionalInterface
  public interface Action {
    boolean execute(ActionContext context);
  }

  @FunctionalInterface
  public interface BeforeAction {
    boolean allow(ActionContext context);
  }

  @FunctionalInterface
  public interface AfterAction {
    void completed(ActionContext context, boolean accepted);
  }

  @FunctionalInterface
  public interface ProjectileFactory {
    Entity create(ProjectileContext context);
  }

  @FunctionalInterface
  public interface BeforeProjectile {
    boolean allow(Identifier id, ProjectileContext context);
  }

  @FunctionalInterface
  public interface AfterProjectile {
    void completed(Identifier id, ProjectileContext context, Entity entity, boolean spawned);
  }

  public record ProjectileContext(
      ServerWorld world, Vec3d position, Vec3d velocity, ItemStack payload, ActionSource source) {
    public ProjectileContext {
      Objects.requireNonNull(world);
      Objects.requireNonNull(position);
      Objects.requireNonNull(velocity);
      Objects.requireNonNull(payload);
      Objects.requireNonNull(source);
      if (!Double.isFinite(
              position.x + position.y + position.z + velocity.x + velocity.y + velocity.z)
          || velocity.lengthSquared() > 256)
        throw new IllegalArgumentException(
            "Projectile vectors must be finite; maximum speed 16 blocks/tick");
      payload = payload.copy();
    }

    @Override
    public ItemStack payload() {
      return payload.copy();
    }
  }

  public record Descriptor(Identifier id, int tier, boolean content, Action action) {
    public Descriptor {
      Objects.requireNonNull(id);
      Objects.requireNonNull(action);
      if (tier < 0 || tier > 4) throw new IllegalArgumentException("tier");
    }
  }

  public static final Event<BeforeAction> BEFORE_ACTION =
      EventFactory.createArrayBacked(
          BeforeAction.class,
          listeners ->
              context -> {
                for (BeforeAction listener : listeners) if (!listener.allow(context)) return false;
                return true;
              });
  public static final Event<AfterAction> AFTER_ACTION =
      EventFactory.createArrayBacked(
          AfterAction.class,
          listeners ->
              (context, accepted) -> {
                for (AfterAction listener : listeners) listener.completed(context, accepted);
              });
  public static final Event<BeforeProjectile> BEFORE_PROJECTILE =
      EventFactory.createArrayBacked(
          BeforeProjectile.class,
          listeners ->
              (id, context) -> {
                for (BeforeProjectile listener : listeners)
                  if (!listener.allow(id, context)) return false;
                return true;
              });
  public static final Event<AfterProjectile> AFTER_PROJECTILE =
      EventFactory.createArrayBacked(
          AfterProjectile.class,
          listeners ->
              (id, context, entity, spawned) -> {
                for (AfterProjectile listener : listeners)
                  listener.completed(id, context, entity, spawned);
              });
  private static final Map<Identifier, Descriptor> ACTIONS = new LinkedHashMap<>();
  private static final Map<Identifier, ProjectileFactory> PROJECTILES = new LinkedHashMap<>();
  private static final Set<Identifier> BUILT_INS = new HashSet<>();
  private static final List<Consumer<Descriptor>> REGISTRATION_LISTENERS = new ArrayList<>();
  private static boolean initialized;
  private static final ThreadLocal<ActionContext> CURRENT = new ThreadLocal<>();

  public static ActionContext currentContext() {
    return CURRENT.get();
  }

  private IcbmApi() {}

  public static synchronized void initialize() {
    if (initialized) return;
    initialized = true;
    for (PayloadType payload : PayloadType.values()) {
      Identifier id = new Identifier("icbm_port", payload.id());
      BUILT_INS.add(id);
      registerAction(
          new Descriptor(
              id,
              payload.tier(),
              payload.hasContent(),
              context ->
                  Payloads.detonateScaled(
                      context.world(),
                      context.position(),
                      payload,
                      context.source().resolve(context.world()),
                      context.size())));
    }
    registerProjectile(
        new Identifier("icbm_port", "explosive_missile"),
        context -> {
          dev.icbm.port.payload.PayloadType type =
              dev.icbm.port.IcbmPort.payloadForMissile(context.payload());
          if (type == null) return null;
          if (context.payload().isOf(dev.icbm.port.delivery.DeliveryContent.CLUSTER_MISSILE))
            return dev.icbm.port.entity.ActionableMissileEntity.forCluster(
                context.world(), context.payload());
          var missile =
              new dev.icbm.port.entity.MissileEntity(
                  dev.icbm.port.IcbmPort.MISSILE_ENTITY, context.world());
          missile.setPayload(type);
          return missile;
        });
    registerProjectile(
        new Identifier("icbm_port", "held_item_missile"),
        context -> {
          if (!dev.icbm.port.delivery.DeliveryContent.isLoadedHeldMissile(context.payload()))
            return null;
          var missile =
              new dev.icbm.port.delivery.HeldItemMissileEntity(
                  dev.icbm.port.delivery.DeliveryContent.HELD_MISSILE, context.world());
          missile.setMissile(context.payload());
          return missile;
        });
  }

  public static synchronized void registerAction(Descriptor descriptor) {
    if (ACTIONS.putIfAbsent(descriptor.id(), descriptor) != null)
      throw new IllegalArgumentException("Duplicate action " + descriptor.id());
    for (Consumer<Descriptor> listener : List.copyOf(REGISTRATION_LISTENERS))
      listener.accept(descriptor);
  }

  /** Replays existing registrations before observing future registrations. */
  public static synchronized void observeRegistrations(Consumer<Descriptor> listener) {
    Objects.requireNonNull(listener);
    List.copyOf(ACTIONS.values()).forEach(listener);
    REGISTRATION_LISTENERS.add(listener);
  }

  public static synchronized List<Descriptor> actions() {
    return List.copyOf(ACTIONS.values());
  }

  public static synchronized Descriptor find(Identifier id) {
    return ACTIONS.get(id);
  }

  public static boolean execute(ActionContext context) {
    if (!context.world().getServer().isOnThread())
      throw new IllegalStateException("Actions require the server thread");
    Descriptor descriptor = find(context.action());
    if (descriptor == null || !context.isLoadedAndInBounds()) return false;
    if (BUILT_INS.contains(context.action())) {
      ActionContext previous = CURRENT.get();
      CURRENT.set(context);
      try {
        return descriptor.action().execute(context);
      } finally {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
      }
    }
    if (!fireBefore(context)) {
      fireAfter(context, false);
      return false;
    }
    boolean accepted = descriptor.action().execute(context);
    fireAfter(context, accepted);
    return accepted;
  }

  public static boolean fireBefore(ActionContext context) {
    return BEFORE_ACTION.invoker().allow(context);
  }

  public static void fireAfter(ActionContext context, boolean accepted) {
    AFTER_ACTION.invoker().completed(context, accepted);
  }

  public static synchronized void registerProjectile(Identifier id, ProjectileFactory factory) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(factory);
    if (PROJECTILES.putIfAbsent(id, factory) != null)
      throw new IllegalArgumentException("Duplicate projectile " + id);
  }

  public static synchronized Set<Identifier> projectiles() {
    return Set.copyOf(PROJECTILES.keySet());
  }

  /** Factory creates an unspawned entity; this method owns bounds checks and the single spawn. */
  public static boolean launch(Identifier id, ProjectileContext context) {
    if (!context.world().getServer().isOnThread())
      throw new IllegalStateException("Projectiles require the server thread");
    var pos = new net.minecraft.util.math.BlockPos(context.position());
    if (!context.world().isChunkLoaded(pos)
        || context.world().isOutOfHeightLimit(pos)
        || !context.world().getWorldBorder().contains(pos)) return false;
    ProjectileFactory factory;
    synchronized (IcbmApi.class) {
      factory = PROJECTILES.get(id);
    }
    if (factory == null) return false;
    if (!BEFORE_PROJECTILE.invoker().allow(id, context)) {
      AFTER_PROJECTILE.invoker().completed(id, context, null, false);
      return false;
    }
    Entity entity = factory.create(context);
    if (entity == null || entity.world != context.world() || entity.isRemoved()) return false;
    entity.setPosition(context.position());
    entity.setVelocity(context.velocity());
    if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity projectile)
      projectile.setOwner(context.source().resolve(context.world()));
    boolean spawned = context.world().spawnEntity(entity);
    AFTER_PROJECTILE.invoker().completed(id, context, entity, spawned);
    return spawned;
  }
}
