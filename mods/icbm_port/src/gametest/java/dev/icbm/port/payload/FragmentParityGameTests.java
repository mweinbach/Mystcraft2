package dev.icbm.port.payload;

import dev.icbm.port.api.ActionSource;
import dev.icbm.port.entity.BlastEntities;
import dev.icbm.port.entity.FragmentEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Fragment parity with upstream {@code BlastShrapnel} and {@code EntityFragments}: the n-by-n
 * yaw/pitch burst, per-payload fragment kinds, hit rules, block interaction, explosive fragments,
 * and the port's live-fragment and per-tick explosion caps.
 */
public final class FragmentParityGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  private static List<FragmentEntity> fragmentsNear(TestContext c, Vec3d center) {
    return c.getWorld()
        .getEntitiesByType(BlastEntities.FRAGMENT, new Box(center, center).expand(3), e -> true);
  }

  private static <T extends LivingEntity> T still(TestContext c, EntityType<T> type, Vec3d pos) {
    T entity = type.create(c.getWorld());
    if (entity instanceof MobEntity mob) mob.setAiDisabled(true);
    entity.setNoGravity(true);
    entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
    c.getWorld().spawnEntity(entity);
    return entity;
  }

  private static FragmentEntity flying(TestContext c, Vec3d pos, Vec3d velocity) {
    FragmentEntity fragment = new FragmentEntity(c.getWorld());
    fragment.setNoGravity(true);
    fragment.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
    fragment.setVelocity(velocity);
    return fragment;
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmFragmentBursts")
  public void shrapnelBurstsIntoNineHundredBurningFragments(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    Payloads.detonate(c.getWorld(), center, PayloadType.SHRAPNEL, null);
    var fragments = fragmentsNear(c, center);
    try {
      check(c, fragments.size() == 900, "Shrapnel must burst into a 30x30 fragment grid");
      check(
          c,
          fragments.stream()
              .allMatch(
                  f ->
                      f.isOnFire()
                          && f.isCritical()
                          && f.getDamage() == 11
                          && !f.isAnvil()
                          && !f.isExplosive()),
          "Shrapnel fragments are burning, critical and deal 11 per unit of speed");
      check(
          c,
          fragments.stream()
              .allMatch(
                  f ->
                      Math.abs(f.getY() - (Math.floor(center.y) + 1.4)) < 1e-6
                          && f.getVelocity().length() < 1.3),
          "Fragments start 1.4 above the blast block at upstream speeds");
    } finally {
      fragments.forEach(FragmentEntity::discard);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmFragmentBursts")
  public void anvilBurstsIntoOwnedHeavyFragments(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    var owner = still(c, EntityType.PIG, Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(0, 2, 0))));
    Payloads.detonate(c.getWorld(), center, PayloadType.ANVIL, owner);
    var fragments = fragmentsNear(c, center);
    try {
      check(c, fragments.size() == 625, "Anvil must burst into a 25x25 fragment grid");
      check(
          c,
          fragments.stream()
              .allMatch(
                  f ->
                      f.isAnvil()
                          && !f.isOnFire()
                          && f.getDamage() == 30
                          && owner.getUuid().equals(f.getOwnerUuid())),
          "Anvil fragments deal 30 and carry the detonating owner");
    } finally {
      fragments.forEach(FragmentEntity::discard);
      owner.discard();
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmFragmentBursts")
  public void fragmentationBurstsIntoExplosiveFragments(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    Payloads.detonate(c.getWorld(), center, PayloadType.FRAGMENTATION, null);
    var fragments = fragmentsNear(c, center);
    try {
      check(c, fragments.size() == 225, "Fragmentation must burst into a 15x15 fragment grid");
      check(
          c,
          fragments.stream()
              .allMatch(
                  f -> f.isExplosive() && f.getExplosionSize() == 1.5f && f.getDamage() == 11),
          "Fragmentation fragments explode with size 1.5");
    } finally {
      fragments.forEach(FragmentEntity::discard);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmFragmentCap")
  public void explosiveFragmentsRespectTheWorldCap(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    int before = c.getWorld().getEntitiesByType(BlastEntities.FRAGMENT, f -> f.isExplosive()).size();
    int spawned =
        FragmentBlast.spawn(
            c.getWorld(),
            center,
            PayloadType.FRAGMENTATION,
            PayloadConfig.forType(PayloadType.FRAGMENTATION),
            FragmentBlast.MAX_GRID,
            ActionSource.of(null));
    var fragments = fragmentsNear(c, center);
    try {
      int expected = Math.max(0, FragmentBlast.MAX_LIVE_EXPLOSIVE - before);
      check(c, spawned == expected, "A 64x64 burst must be thinned to the live explosive cap");
      check(
          c,
          c.getWorld().getEntitiesByType(BlastEntities.FRAGMENT, f -> f.isExplosive()).size()
              <= FragmentBlast.MAX_LIVE_EXPLOSIVE,
          "Live explosive fragments must never exceed the cap");
    } finally {
      fragments.forEach(FragmentEntity::discard);
    }
    c.complete();
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmFragmentExplosions",
      tickLimit = 40)
  public void fragmentExplosionsAreBudgetedPerTick(TestContext c) {
    Vec3d pos = c.getAbsolute(new Vec3d(2.5, 4, 2.5));
    List<FragmentEntity> fragments = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      FragmentEntity fragment = flying(c, pos, Vec3d.ZERO);
      fragment.setExplosive(true);
      fragment.setExplosionSize(.5f);
      c.getWorld().spawnEntity(fragment);
      fragments.add(fragment);
    }
    int start = FragmentEntity.explosionsThisTick(c.getWorld());
    fragments.forEach(FragmentEntity::explode);
    long exploded = fragments.stream().filter(FragmentEntity::isRemoved).count();
    check(
        c,
        exploded == FragmentEntity.MAX_EXPLOSIONS_PER_TICK - start
            && FragmentEntity.explosionsThisTick(c.getWorld())
                == FragmentEntity.MAX_EXPLOSIONS_PER_TICK,
        "Only the per-tick explosion budget may go off at once");
    check(
        c,
        fragments.stream().filter(f -> !f.isRemoved()).allMatch(FragmentEntity::isPendingExplosion),
        "Fragments over budget must wait for a later tick");
    c.runAtTick(
        10,
        () -> {
          check(
              c,
              fragments.stream().allMatch(FragmentEntity::isRemoved),
              "Deferred fragments must explode on following ticks");
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmFragmentHits",
      tickLimit = 40)
  public void burningFragmentHitsWithOwnerAttribution(TestContext c) {
    var target = still(c, EntityType.IRON_GOLEM, c.getAbsolute(new Vec3d(4.5, 2, 2.5)));
    var owner = still(c, EntityType.PIG, c.getAbsolute(new Vec3d(.5, 2, 4.5)));
    FragmentEntity fragment =
        flying(c, c.getAbsolute(new Vec3d(.5, 3.2, 2.5)), new Vec3d(.5, 0, 0));
    fragment.setDamage(11);
    fragment.setOnFireFor(100);
    fragment.setOwner(owner);
    c.getWorld().spawnEntity(fragment);
    float full = target.getHealth();
    c.runAtTick(
        15,
        () -> {
          try {
            check(c, fragment.isRemoved(), "A fragment that damages its target is consumed");
            // About 0.47 blocks/tick at impact times 11 damage, rounded up.
            check(c, target.getHealth() == full - 6, "Hit damage must be ceil(speed * damage)");
            check(
                c,
                target.getRecentDamageSource() instanceof ProjectileDamageSource source
                    && source.getSource() == fragment
                    && source.getAttacker() == owner
                    && source.isProjectile(),
                "Fragment damage must name the fragment and its owner");
            check(c, target.isOnFire(), "A burning fragment must ignite its target");
          } finally {
            target.discard();
            owner.discard();
          }
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmFragmentHits",
      tickLimit = 40)
  public void explosiveFragmentSplashCreditsItsOwner(TestContext c) {
    var target = still(c, EntityType.IRON_GOLEM, c.getAbsolute(new Vec3d(3.5, 2, 2.5)));
    var owner = still(c, EntityType.PIG, c.getAbsolute(new Vec3d(.5, 2, 6.5)));
    FragmentEntity fragment = flying(c, c.getAbsolute(new Vec3d(2.5, 3, 2.5)), Vec3d.ZERO);
    fragment.setExplosive(true);
    fragment.setExplosionSize(1.5f);
    fragment.setOwner(owner);
    c.getWorld().spawnEntity(fragment);
    // explode() may defer to a later tick under the per-tick explosion budget.
    c.runAtTick(2, fragment::explode);
    c.runAtTick(
        12,
        () -> {
          try {
            check(c, fragment.isRemoved(), "The explosive fragment must have exploded");
            var source = target.getRecentDamageSource();
            check(
                c,
                source != null && source.isExplosive() && source.getAttacker() == owner,
                "Fragment explosion damage must name the fragment's owner");
          } finally {
            target.discard();
            owner.discard();
          }
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmFragmentHits",
      tickLimit = 20)
  public void fragmentsCannotHitDuringTheirFirstTicks(TestContext c) {
    var target = still(c, EntityType.IRON_GOLEM, c.getAbsolute(new Vec3d(4.5, 2, 2.5)));
    FragmentEntity fragment =
        flying(c, c.getAbsolute(new Vec3d(3.2, 3.2, 2.5)), new Vec3d(.5, 0, 0));
    c.getWorld().spawnEntity(fragment);
    float full = target.getHealth();
    c.runAtTick(
        3,
        () -> {
          try {
            check(c, !fragment.isRemoved(), "Fragment must still be flying");
            check(c, target.getHealth() == full, "Upstream ignores entities for 5 airborne ticks");
          } finally {
            fragment.discard();
            target.discard();
          }
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmFragmentHits",
      tickLimit = 30)
  public void fragmentsBreakGlassAndStopInStone(TestContext c) {
    BlockPos stone = c.getAbsolutePos(new BlockPos(1, 2, 1));
    BlockPos glass = c.getAbsolutePos(new BlockPos(4, 3, 3));
    c.getWorld().setBlockState(stone, Blocks.STONE.getDefaultState(), 3);
    c.getWorld().setBlockState(glass, Blocks.GLASS.getDefaultState(), 3);
    FragmentEntity falling = flying(c, c.getAbsolute(new Vec3d(1.5, 4.5, 1.5)), new Vec3d(0, -.5, 0));
    falling.setNoGravity(false);
    FragmentEntity breaker =
        flying(c, c.getAbsolute(new Vec3d(2, 3.25, 3.5)), new Vec3d(.5, 0, 0));
    c.getWorld().spawnEntity(falling);
    c.getWorld().spawnEntity(breaker);
    c.runAtTick(
        15,
        () -> {
          try {
            check(c, falling.isRemoved(), "A fragment that lands in stone must end");
            check(
                c, c.getWorld().getBlockState(stone).isOf(Blocks.STONE), "Stone must survive");
            check(c, c.getWorld().getBlockState(glass).isAir(), "Fragments must break glass");
          } finally {
            breaker.discard();
          }
          c.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmFragmentHits")
  public void fragmentStateSurvivesReload(TestContext c) {
    var owner = EntityType.PIG.create(c.getWorld());
    FragmentEntity fragment = new FragmentEntity(c.getWorld());
    fragment.setAnvil(true).setCritical(true).setDamage(30).setExplosionSize(2);
    fragment.setOwner(owner);
    fragment.setVelocity(.25, -.5, .75);
    NbtCompound saved = new NbtCompound();
    fragment.writeNbt(saved);
    FragmentEntity restored = new FragmentEntity(c.getWorld());
    restored.readNbt(saved);
    check(
        c,
        restored.isAnvil()
            && restored.isCritical()
            && !restored.isExplosive()
            && restored.getDamage() == 30
            && restored.getExplosionSize() == 2
            && owner.getUuid().equals(restored.getOwnerUuid())
            && restored.getVelocity().squaredDistanceTo(.25, -.5, .75) < 1e-9,
        "Fragment kind, damage, owner and motion must survive reload");
    c.complete();
  }
}
