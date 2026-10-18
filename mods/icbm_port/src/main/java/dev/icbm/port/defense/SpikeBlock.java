package dev.icbm.port.defense;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/** Upstream normal/poison/fire spike overlap damage and seven-second secondary effects. */
public final class SpikeBlock extends Block {
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
    super(Settings.of(Material.METAL).strength(1).noCollision().nonOpaque());
    this.kind = kind;
  }

  @Override
  public VoxelShape getOutlineShape(
      BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
    return Block.createCuboidShape(0, 0, 0, 16, 8, 16);
  }

  @Override
  public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
    if (!world.isClient && entity instanceof LivingEntity living) {
      living.damage(DamageSource.CACTUS, 1);
      if (kind == Kind.POISON)
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 140, 0));
      else if (kind == Kind.FIRE) living.setOnFireFor(7);
    }
  }
}
