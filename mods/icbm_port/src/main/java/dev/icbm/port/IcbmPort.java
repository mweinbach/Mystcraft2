package dev.icbm.port;

import dev.icbm.port.block.CondensedExplosiveBlock;
import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.defense.DefenseContent;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.launcher.LauncherContent;
import dev.icbm.port.payload.PayloadType;
import dev.icbm.port.payload.Payloads;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Material;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class IcbmPort implements ModInitializer {
  public static final String MOD_ID = "icbm_port";
  // Upstream condensed energy scale is 6. Blast damage/shape parity is still pending.
  public static final float BLAST_POWER = 6.0F;
  public static final int FUSE_TICKS = 100;
  public static final double MISSILE_SPEED = 1.5;
  public static final int MISSILE_LIFETIME = 200;
  public static final ItemGroup GROUP =
      FabricItemGroupBuilder.build(id("items"), () -> new ItemStack(Items.FIREWORK_ROCKET));
  public static final CondensedExplosiveBlock CONDENSED_EXPLOSIVE =
      new CondensedExplosiveBlock(AbstractBlock.Settings.of(Material.TNT).breakInstantly());
  public static final LauncherBlock LAUNCHER =
      new LauncherBlock(AbstractBlock.Settings.of(Material.METAL).strength(3.5F).requiresTool());
  public static final Item CONDENSED_MISSILE =
      new Item(new Item.Settings().group(GROUP).maxCount(16));
  public static final EntityType<MissileEntity> MISSILE_ENTITY =
      FabricEntityTypeBuilder.<MissileEntity>create(SpawnGroup.MISC, MissileEntity::new)
          .dimensions(EntityDimensions.fixed(0.35F, 0.35F))
          .trackRangeBlocks(128)
          .trackedUpdateRate(1)
          .build();
  public static final EntityType<PrimedExplosiveEntity> PRIMED_EXPLOSIVE =
      FabricEntityTypeBuilder.<PrimedExplosiveEntity>create(
              SpawnGroup.MISC, PrimedExplosiveEntity::new)
          .dimensions(EntityDimensions.fixed(0.98F, 0.98F))
          .trackRangeBlocks(64)
          .trackedUpdateRate(1)
          .build();

  public static final Map<PayloadType, CondensedExplosiveBlock> EXPLOSIVE_BLOCKS =
      new EnumMap<>(PayloadType.class);
  public static final Map<PayloadType, Item> MISSILE_ITEMS = new EnumMap<>(PayloadType.class);

  public static boolean isMissile(ItemStack stack) {
    return payloadForMissile(stack) != null || DeliveryContent.isLoadedHeldMissile(stack);
  }

  public static PayloadType payloadById(String id) {
    return PayloadType.fromId(id);
  }

  public static PayloadType payloadForMissile(ItemStack stack) {
    if (stack.isEmpty()) return null;
    if (stack.isOf(DeliveryContent.CLUSTER_MISSILE))
      return dev.icbm.port.delivery.ClusterPlan.fromStack(stack).isEmpty()
          ? null
          : DeliveryContent.clusterPayload(stack);
    for (var entry : MISSILE_ITEMS.entrySet())
      if (stack.isOf(entry.getValue())) return entry.getKey();
    return null;
  }

  public static Identifier id(String path) {
    return new Identifier(MOD_ID, path);
  }

  @Override
  public void onInitialize() {
    for (PayloadType payload : PayloadType.values()) {
      if (!payload.hasContent()) continue;
      CondensedExplosiveBlock block =
          payload == PayloadType.CONDENSED
              ? CONDENSED_EXPLOSIVE
              : payload == PayloadType.BREACHING
                  ? new dev.icbm.port.block.BreachingExplosiveBlock(
                      AbstractBlock.Settings.of(Material.TNT).breakInstantly())
                  : payload == PayloadType.ENDER
                      ? new dev.icbm.port.block.EnderExplosiveBlock(
                          AbstractBlock.Settings.of(Material.TNT).breakInstantly())
                      : new CondensedExplosiveBlock(
                          AbstractBlock.Settings.of(Material.TNT).breakInstantly(), payload);
      Item missile =
          payload == PayloadType.CONDENSED
              ? CONDENSED_MISSILE
              : new Item(new Item.Settings().group(GROUP).maxCount(16));
      EXPLOSIVE_BLOCKS.put(payload, block);
      MISSILE_ITEMS.put(payload, missile);
      Registry.register(Registry.BLOCK, id(payload.id() + "_explosive"), block);
      Registry.register(
          Registry.ITEM,
          id(payload.id() + "_explosive"),
          new BlockItem(block, new Item.Settings().group(GROUP)));
      Registry.register(Registry.ITEM, id(payload.id() + "_missile"), missile);
    }
    if (EXPLOSIVE_BLOCKS.get(PayloadType.ENDER) instanceof dev.icbm.port.block.EnderExplosiveBlock ender)
      dev.icbm.port.block.EnderExplosiveBlock.register(ender);
    Registry.register(Registry.BLOCK, id("launcher"), LAUNCHER);
    Registry.register(
        Registry.ITEM, id("launcher"), new BlockItem(LAUNCHER, new Item.Settings().group(GROUP)));
    Registry.register(Registry.ENTITY_TYPE, id("condensed_missile"), MISSILE_ENTITY);
    Registry.register(Registry.ENTITY_TYPE, id("primed_condensed_explosive"), PRIMED_EXPLOSIVE);
    dev.icbm.port.content.Materials.register();
    dev.icbm.port.energy.EnergyContent.register();
    dev.icbm.port.exposure.ExposureContent.register();
    dev.icbm.port.simulation.SimulationContent.register();
    dev.icbm.port.entity.BlastEntities.register();
    Payloads.initialize();
    dev.icbm.port.entity.EntityContent.register();
    LauncherContent.register();
    DeliveryContent.register();
    DefenseContent.register();
    dev.icbm.port.api.IcbmApi.initialize();
    dev.icbm.port.command.IcbmCommands.register();
  }
}
