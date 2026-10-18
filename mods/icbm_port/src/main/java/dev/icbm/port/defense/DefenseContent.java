package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.delivery.HeldItemMissileEntity;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import java.util.*;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

/**
 * Gameplay adaptations of upstream radar, EMP, antidote, defuser and protection blocks. Machines
 * deliberately use redstone rather than reproducing Forge energy or radio GUIs.
 */
public final class DefenseContent {
  public static final EntityType<SamInterceptorEntity> SAM_INTERCEPTOR =
      FabricEntityTypeBuilder.<SamInterceptorEntity>create(
              SpawnGroup.MISC, SamInterceptorEntity::new)
          .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
          .trackRangeBlocks(128)
          .trackedUpdateRate(1)
          .build();
  public static final DefenseMachineBlock RADAR_STATION =
      new DefenseMachineBlock(DefenseMachineBlock.Kind.RADAR);
  public static final DefenseMachineBlock EMP_TOWER =
      new DefenseMachineBlock(DefenseMachineBlock.Kind.EMP);
  public static final DefenseMachineBlock SAM_LAUNCHER =
      new DefenseMachineBlock(DefenseMachineBlock.Kind.SAM);
  public static final Block CONCRETE =
      new Block(AbstractBlock.Settings.of(Material.STONE).strength(10, 28).requiresTool());
  public static final Block COMPACT_CONCRETE =
      new Block(AbstractBlock.Settings.of(Material.STONE).strength(10, 280).requiresTool());
  public static final Block REINFORCED_CONCRETE =
      new Block(AbstractBlock.Settings.of(Material.STONE).strength(10, 2800).requiresTool());
  public static final Block REINFORCED_GLASS =
      new GlassBlock(AbstractBlock.Settings.of(Material.GLASS).strength(3, 60).nonOpaque());
  public static final Block SPIKES = new SpikeBlock();
  public static final Block POISON_SPIKES = new SpikeBlock(SpikeBlock.Kind.POISON);
  public static final Block FIRE_SPIKES = new SpikeBlock(SpikeBlock.Kind.FIRE);
  public static final Block GLASS_BUTTON =
      new StoneButtonBlock(AbstractBlock.Settings.of(Material.GLASS).noCollision().strength(0.5F));
  public static final Block GLASS_PRESSURE_PLATE =
      new PressurePlateBlock(
          PressurePlateBlock.ActivationRule.EVERYTHING,
          AbstractBlock.Settings.of(Material.GLASS).noCollision().strength(0.5F));
  public static final Item SAM_MISSILE =
      new Item(new Item.Settings().group(IcbmPort.GROUP).maxCount(16));
  public static final Item RADAR_GUN = new RadarGunItem();
  public static final Item DEFUSER =
      new Item(new Item.Settings().group(IcbmPort.GROUP).maxDamage(128));
  public static final Item ANTIDOTE = new AntidoteItem();

  public static final Block EMP_TOWER_EXTENSION = new EmpTowerExtensionBlock();
  public static final net.minecraft.block.entity.BlockEntityType<DefenseMachineBlockEntity>
      MACHINE_BLOCK_ENTITY =
          net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
              .create(DefenseMachineBlockEntity::new, RADAR_STATION, EMP_TOWER, SAM_LAUNCHER)
              .build();
  public static net.minecraft.screen.ScreenHandlerType<DefenseScreenHandler> MACHINE_SCREEN;

  private DefenseContent() {}

  public static void register() {
    Registry.register(
        Registry.BLOCK_ENTITY_TYPE, IcbmPort.id("defense_machine"), MACHINE_BLOCK_ENTITY);
    MACHINE_SCREEN =
        net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry.registerSimple(
            IcbmPort.id("defense_machine"), DefenseScreenHandler::new);
    block("emp_tower_extension", EMP_TOWER_EXTENSION);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("sam_interceptor"), SAM_INTERCEPTOR);
    dev.icbm.port.payload.Payloads.EMP_HANDLERS.add(
        (world, pos) ->
            applyEmp(
                world,
                pos,
                dev.icbm.port.payload.PayloadConfig.forType(dev.icbm.port.payload.PayloadType.EMP)
                    .radius()));
    block("radar_station", RADAR_STATION);
    block("emp_tower", EMP_TOWER);
    block("sam_launcher", SAM_LAUNCHER);
    block("concrete", CONCRETE);
    block("compact_concrete", COMPACT_CONCRETE);
    block("reinforced_concrete", REINFORCED_CONCRETE);
    block("poison_spikes", POISON_SPIKES);
    block("fire_spikes", FIRE_SPIKES);
    block("reinforced_glass", REINFORCED_GLASS);
    block("spikes", SPIKES);
    block("glass_button", GLASS_BUTTON);
    block("glass_pressure_plate", GLASS_PRESSURE_PLATE);
    item("sam_missile", SAM_MISSILE);
    item("radar_gun", RADAR_GUN);
    item("defuser", DEFUSER);
    item("antidote", ANTIDOTE);
    AttackEntityCallback.EVENT.register(
        (player, world, hand, target, hit) -> {
          if (player.isSpectator()) return ActionResult.PASS;
          ItemStack held = player.getStackInHand(hand);
          if (held.isOf(DEFUSER) && canDefuse(target)) {
            if (!world.isClient) {
              if (target instanceof TntEntity) target.dropItem(Items.TNT);
              else if (target instanceof PrimedExplosiveEntity explosive)
                target.dropStack(explosive.getStack().copy());
              else if (target instanceof MissileEntity missile)
                target.dropStack(missile.getStack().copy());
              if (target instanceof HeldItemMissileEntity heldMissile) heldMissile.recover();
              else target.discard();
              held.damage(1, player, p -> p.sendToolBreakStatus(hand));
            }
            return ActionResult.SUCCESS;
          }
          return ActionResult.PASS;
        });
  }

  private static void block(String name, Block block) {
    Registry.register(Registry.BLOCK, IcbmPort.id(name), block);
    item(name, new BlockItem(block, new Item.Settings().group(IcbmPort.GROUP)));
  }

  private static void item(String name, Item item) {
    Registry.register(Registry.ITEM, IcbmPort.id(name), item);
  }

  public static void registerClient() {
    DefenseClient.register();
  }

  public static boolean canDefuse(Entity entity) {
    return entity instanceof SamInterceptorEntity
        || entity instanceof HeldItemMissileEntity
        || entity instanceof MissileEntity
        || entity instanceof PrimedExplosiveEntity
        || entity instanceof TntEntity;
  }

  /** Bounded loaded-entity query; no chunk loading and no explosion on interception. */
  public static List<MissileEntity> missiles(World world, Vec3d center, double radius) {
    double bounded = Math.max(0, Math.min(512, radius));
    return world.getEntitiesByClass(
        MissileEntity.class,
        new Box(center, center).expand(bounded),
        e -> !e.isRemoved() && e.squaredDistanceTo(center) <= bounded * bounded);
  }

  /** All loaded missile families, including nonexplosive cargo/tool flights. */
  public static List<Entity> trackedMissiles(World world, Vec3d center, double radius) {
    if (!Double.isFinite(radius) || radius < 0) return List.of();
    double bounded = Math.min(512, radius);
    return world.getEntitiesByClass(
        Entity.class,
        new Box(center, center).expand(bounded),
        entity ->
            isTrackedMissile(entity)
                && !entity.isRemoved()
                && entity.squaredDistanceTo(center) <= bounded * bounded);
  }

  public static boolean isTrackedMissile(Entity entity) {
    return entity instanceof MissileEntity || entity instanceof HeldItemMissileEntity;
  }

  public static boolean isFlightDisabled(Entity entity) {
    return entity instanceof MissileEntity missile
        ? missile.isEmpDisabled()
        : entity instanceof HeldItemMissileEntity held && held.isEmpDisabled();
  }

  public static void neutralizeFlight(Entity entity) {
    if (entity instanceof HeldItemMissileEntity held) held.recover();
    else if (entity instanceof MissileEntity) entity.discard();
  }

  public static void applyEmp(World world, Vec3d center, double radius) {
    if (world.isClient
        || !Double.isFinite(radius)
        || radius <= 0
        || !Double.isFinite(center.x)
        || !Double.isFinite(center.y)
        || !Double.isFinite(center.z)) return;
    double bounded = Math.min(512, radius);
    if (!(world instanceof net.minecraft.server.world.ServerWorld server)) return;
    EmpPersistentState.get(server).add(center, bounded, world.getTime());
    trackedMissiles(world, center, bounded)
        .forEach(
            entity -> {
              if (entity instanceof MissileEntity missile) missile.disableByEmp(200);
              else if (entity instanceof HeldItemMissileEntity held) held.disableByEmp(200);
            });
    world
        .getEntitiesByClass(
            SamInterceptorEntity.class,
            new Box(center, center).expand(bounded),
            entity -> entity.squaredDistanceTo(center) <= bounded * bounded)
        .forEach(Entity::discard);
  }

  /** Ignore receding missiles and targets already assigned to a loaded interceptor. */
  public static boolean isThreat(World world, Vec3d station, Entity missile) {
    if (!isTrackedMissile(missile) || missile.isRemoved() || isFlightDisabled(missile))
      return false;
    Vec3d motion = missile.getVelocity();
    Vec3d toward = station.subtract(missile.getPos());
    if (motion.lengthSquared() > 1e-8 && toward.dotProduct(motion) <= 0) return false;
    if (motion.lengthSquared() > 1e-8) {
      double approach =
          MathHelper.clamp(toward.dotProduct(motion) / motion.lengthSquared(), 0, 100);
      if (missile.getPos().add(motion.multiply(approach)).squaredDistanceTo(station) > 16 * 16)
        return false;
    }
    return world
        .getEntitiesByClass(
            SamInterceptorEntity.class,
            new Box(station, station).expand(160),
            e -> !e.isRemoved() && missile.getUuid().equals(e.getTargetUuid()))
        .isEmpty();
  }

  public static boolean isEmpDisabled(World world, BlockPos pos) {
    return world instanceof net.minecraft.server.world.ServerWorld server
        && EmpPersistentState.get(server).contains(Vec3d.ofCenter(pos), world.getTime());
  }
}
