package dev.icbm.port.defense;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.*;

/**
 * Normal/poison/fire spikes, adapted from upstream ICBM-Classic {@code
 * icbm/classic/content/blocks/BlockSpikes.java} (MIT). Iron material (pickaxe to harvest), hardness
 * and resistance 1, no collision, full-cube selection box, configurable damage from a dedicated
 * damage source on every overlap tick (upstream has no cooldown beyond vanilla hurt immunity),
 * 7-second poison or fire.
 */
public final class SpikeBlock extends Block {
  /** Upstream {@code icbmclassic:spikes}; port death message key is my own wording. */
  public static final DamageSource DAMAGE_SOURCE = new DamageSource("icbm_port.spikes");

  public static final int POISON_TICKS = 140;
  public static final int FIRE_SECONDS = 7;

  public enum Kind {
    NORMAL,
    POISON,
    FIRE
  }

  private final Kind kind;

  SpikeBlock() {
    this(Kind.NORMAL);
  }

  SpikeBlock(Kind kind) {
    super(Settings.of(Material.METAL).strength(1).requiresTool().noCollision().nonOpaque());
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  @Override
  public VoxelShape getOutlineShape(
      BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
    return VoxelShapes.fullCube();
  }

  @Override
  public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
    if (!world.isClient && entity instanceof LivingEntity living) {
      living.damage(DAMAGE_SOURCE, DefenseContent.blockConfig.damageFor(kind));
      if (kind == Kind.POISON)
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, POISON_TICKS, 0));
      else if (kind == Kind.FIRE) living.setOnFireFor(FIRE_SECONDS);
    }
  }
}
