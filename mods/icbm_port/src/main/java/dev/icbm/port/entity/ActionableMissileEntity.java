package dev.icbm.port.entity;

import dev.icbm.port.api.ActionContext;
import dev.icbm.port.api.ActionSource;
import dev.icbm.port.api.IcbmApi;
import dev.icbm.port.delivery.ClusterPlan;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.payload.PayloadConfig;
import dev.icbm.port.payload.PayloadType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Missile whose effect is a serialized action payload rather than a fixed explosive.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/missile/entity/explosive/EntityMissileActionable
 * .java} with the trigger rules of {@code content/cluster/missile/CapabilityClusterMissileStack.java}
 * ({@code ConditionalImpact(false)} AND {@code ConditionTargetDistance(3)}), the EMP/destroy
 * triggers and {@code ItemRocketLauncher}'s cluster burst height. Flight, health, fuel, EMP and
 * persistence reuse {@link MissileEntity}.
 *
 * <p>The action is stored as NBT {@code {Type: <id>, ...}} and decoded through {@link
 * #registerActionType}. Built-in types: {@code icbm_port:api_action} runs any {@link IcbmApi}
 * action (payloads and addon actions) with a saved size, and {@code icbm_port:cluster} releases a
 * bounded {@link ClusterPlan}. Triggers: proximity to a saved target (upstream default 3 blocks),
 * impact (can be disabled as upstream clusters do; a disabled impact is a dud), destruction by
 * explosion or fire damage (upstream {@code onDestroyedBy}), and — as a documented port fallback
 * for flights with no target — the existing 40-tick cluster airburst or arrival.
 */
public class ActionableMissileEntity extends MissileEntity {
  /** Upstream {@code ConditionTargetDistance#setTriggerDistance(3)} for cluster missiles. */
  public static final double CLUSTER_TRIGGER_DISTANCE = 3;

  /** Upstream {@code ItemRocketLauncher.ballisticBurstY}: clusters aim 20 blocks above a target. */
  public static final double BALLISTIC_BURST_HEIGHT = 20;

  public static final Identifier API_ACTION = new Identifier("icbm_port", "api_action");
  public static final Identifier CLUSTER_ACTION = new Identifier("icbm_port", "cluster");

  /** Executable, serializable payload carried by the missile. */
  public interface MissileAction {
    Identifier type();

    /** Fields other than {@code Type}. */
    NbtCompound write();

    /** Runs on the server thread; returns whether the action accepted the trigger. */
    boolean run(ServerWorld world, Vec3d position, ActionableMissileEntity host);
  }

  private static final Map<Identifier, Function<NbtCompound, MissileAction>> TYPES =
      new LinkedHashMap<>();

  static {
    registerActionType(API_ACTION, ApiAction::read);
    registerActionType(CLUSTER_ACTION, ClusterAction::read);
  }

  /** Registers an addon action decoder. Duplicate type ids are rejected. */
  public static synchronized void registerActionType(
      Identifier type, Function<NbtCompound, MissileAction> reader) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(reader);
    if (TYPES.putIfAbsent(type, reader) != null)
      throw new IllegalArgumentException("Duplicate missile action type " + type);
  }

  @Nullable
  public static synchronized MissileAction readAction(NbtCompound nbt) {
    Identifier type = Identifier.tryParse(nbt.getString("Type"));
    Function<NbtCompound, MissileAction> reader = type == null ? null : TYPES.get(type);
    if (reader == null) return null;
    try {
      return reader.apply(nbt);
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  public static NbtCompound writeAction(MissileAction action) {
    NbtCompound nbt = action.write().copy();
    nbt.putString("Type", action.type().toString());
    return nbt;
  }

  /** {@link IcbmApi} action id plus absolute size; a negative size means the configured radius. */
  public record ApiAction(Identifier action, double size) implements MissileAction {
    public ApiAction {
      Objects.requireNonNull(action);
      if (!Double.isFinite(size)) throw new IllegalArgumentException("size");
      size = Math.min(PayloadType.MAX_EXTREME_RADIUS, size);
    }

    static ApiAction read(NbtCompound nbt) {
      Identifier id = Identifier.tryParse(nbt.getString("Action"));
      if (id == null) throw new IllegalArgumentException("action");
      return new ApiAction(id, nbt.contains("Size") ? nbt.getDouble("Size") : -1);
    }

    @Override
    public Identifier type() {
      return API_ACTION;
    }

    @Override
    public NbtCompound write() {
      NbtCompound nbt = new NbtCompound();
      nbt.putString("Action", action.toString());
      nbt.putDouble("Size", size);
      return nbt;
    }

    public double resolvedSize() {
      if (size >= 0) return size;
      if (action.getNamespace().equals("icbm_port")) {
        PayloadType payload = PayloadType.fromId(action.getPath());
        if (payload != null && payload.id().equals(action.getPath()))
          return Math.min(payload.maxRadius(), PayloadConfig.forType(payload).radius());
      }
      return 0;
    }

    @Override
    public boolean run(ServerWorld world, Vec3d position, ActionableMissileEntity host) {
      if (IcbmApi.find(action) == null) return false;
      return IcbmApi.execute(
          new ActionContext(world, position, action, resolvedSize(), ActionSource.of(host)));
    }
  }

  /** Upstream {@code ActionDataCluster}: bounded mixed bomblet manifest. */
  public record ClusterAction(ClusterPlan plan) implements MissileAction {
    public ClusterAction {
      Objects.requireNonNull(plan);
    }

    static ClusterAction read(NbtCompound nbt) {
      return new ClusterAction(ClusterPlan.read(nbt));
    }

    @Override
    public Identifier type() {
      return CLUSTER_ACTION;
    }

    @Override
    public NbtCompound write() {
      NbtCompound nbt = new NbtCompound();
      plan.writeTo(nbt);
      return nbt;
    }

    @Override
    public boolean run(ServerWorld world, Vec3d position, ActionableMissileEntity host) {
      return DeliveryContent.burst(world, position, plan, host, host.getVelocity()) > 0;
    }
  }

  @Nullable private MissileAction action;
  private ItemStack originalStack = ItemStack.EMPTY;
  @Nullable private Vec3d triggerTarget;
  private double triggerDistance = CLUSTER_TRIGGER_DISTANCE;
  private boolean impactTrigger = true;
  private boolean triggered;

  public ActionableMissileEntity(EntityType<? extends ActionableMissileEntity> type, World world) {
    super(type, world);
  }

  /** Cluster missile item to actionable entity, as upstream {@code CapabilityClusterMissileStack}. */
  @Nullable
  public static ActionableMissileEntity forCluster(World world, ItemStack stack) {
    ClusterPlan plan = ClusterPlan.fromStack(stack);
    if (plan.isEmpty()) return null;
    ActionableMissileEntity missile = new ActionableMissileEntity(EntityContent.ACTIONABLE_MISSILE, world);
    missile.setPayload(plan.payloads().get(0));
    missile.setCluster(true);
    missile.setClusterPlan(plan);
    missile.setAction(new ClusterAction(plan));
    missile.setOriginalStack(stack);
    return missile;
  }

  /** Any registered {@link IcbmApi} action; {@code size < 0} uses the configured radius. */
  public static ActionableMissileEntity forApiAction(
      World world, Identifier action, double size, ItemStack originalStack) {
    ActionableMissileEntity missile = new ActionableMissileEntity(EntityContent.ACTIONABLE_MISSILE, world);
    missile.setAction(new ApiAction(action, size));
    missile.setOriginalStack(originalStack);
    return missile;
  }

  public void setAction(@Nullable MissileAction action) {
    this.action = action;
  }

  @Nullable
  public MissileAction getAction() {
    return action;
  }

  public void setOriginalStack(ItemStack stack) {
    originalStack = stack.copy();
    originalStack.setCount(Math.min(1, originalStack.getCount()));
    if (!originalStack.isEmpty()) setItem(originalStack.copy());
  }

  public ItemStack getOriginalStack() {
    return originalStack.copy();
  }

  /**
   * Upstream cluster trigger: fire when within {@code distance} of {@code target} and, unless
   * {@code impactTrigger}, do nothing on impact.
   */
  public void setProximityTrigger(@Nullable Vec3d target, double distance, boolean impactTrigger) {
    this.triggerTarget = target == null || !Double.isFinite(target.x + target.y + target.z) ? null : target;
    this.triggerDistance = Math.max(0, Math.min(64, distance));
    this.impactTrigger = impactTrigger;
  }

  @Nullable
  public Vec3d getTriggerTarget() {
    return triggerTarget;
  }

  public double getTriggerDistance() {
    return triggerDistance;
  }

  public boolean hasImpactTrigger() {
    return impactTrigger;
  }

  public boolean hasTriggered() {
    return triggered;
  }

  @Override
  protected boolean shouldTriggerInFlight() {
    if (triggerTarget != null)
      return getPos().distanceTo(triggerTarget) < triggerDistance || flightPlan().arrived();
    return super.shouldTriggerInFlight();
  }

  @Override
  protected void detonate(Vec3d position) {
    discard();
    runAction(position);
  }

  @Override
  protected void onImpact(Vec3d position) {
    // Discrete ticks: a hit within one tick of travel of the trigger sphere would have crossed it.
    boolean reachedTarget =
        triggerTarget != null
            && position.distanceTo(triggerTarget) <= triggerDistance + getVelocity().length();
    if (impactTrigger || reachedTarget) detonate(position);
    else discard(); // Upstream ConditionalImpact(false) blocks the action: the missile is a dud.
  }

  /** Upstream {@code onDestroyedBy}: explosions and fire set the payload off. */
  @Override
  protected void onDestroyedBy(DamageSource source) {
    if (source.isExplosive() || source.isFire()) {
      discard();
      runAction(getPos());
    }
  }

  /** Executes the payload once. Returns whether the action accepted the trigger. */
  public boolean runAction(Vec3d position) {
    if (triggered || !(world instanceof ServerWorld server) || action == null) return false;
    triggered = true;
    return action.run(server, position, this);
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    if (action != null) nbt.put("MissileAction", writeAction(action));
    if (!originalStack.isEmpty()) nbt.put("OriginalStack", originalStack.writeNbt(new NbtCompound()));
    if (triggerTarget != null) {
      NbtCompound target = new NbtCompound();
      target.putDouble("X", triggerTarget.x);
      target.putDouble("Y", triggerTarget.y);
      target.putDouble("Z", triggerTarget.z);
      nbt.put("TriggerTarget", target);
    }
    nbt.putDouble("TriggerDistance", triggerDistance);
    nbt.putBoolean("ImpactTrigger", impactTrigger);
    nbt.putBoolean("Triggered", triggered);
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    action = nbt.contains("MissileAction") ? readAction(nbt.getCompound("MissileAction")) : null;
    originalStack =
        nbt.contains("OriginalStack") ? ItemStack.fromNbt(nbt.getCompound("OriginalStack")) : ItemStack.EMPTY;
    if (!originalStack.isEmpty()) setItem(originalStack.copy());
    triggerTarget = null;
    if (nbt.contains("TriggerTarget")) {
      NbtCompound target = nbt.getCompound("TriggerTarget");
      Vec3d value = new Vec3d(target.getDouble("X"), target.getDouble("Y"), target.getDouble("Z"));
      if (Double.isFinite(value.x + value.y + value.z)) triggerTarget = value;
    }
    triggerDistance =
        nbt.contains("TriggerDistance")
            ? Math.max(0, Math.min(64, nbt.getDouble("TriggerDistance")))
            : CLUSTER_TRIGGER_DISTANCE;
    impactTrigger = !nbt.contains("ImpactTrigger") || nbt.getBoolean("ImpactTrigger");
    triggered = nbt.getBoolean("Triggered");
  }
}
