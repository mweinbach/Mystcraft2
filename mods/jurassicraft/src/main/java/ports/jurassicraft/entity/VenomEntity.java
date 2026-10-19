package ports.jurassicraft.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import ports.jurassicraft.content.Species;

/** Dilophosaurus venom blinds and disorients its target, as in the original mod. */
public final class VenomEntity extends ThrowableProjectile {
    public VenomEntity(EntityType<? extends VenomEntity> type, Level level) { super(type, level); }

    VenomEntity(Level level, DinosaurEntity spitter) {
        super(DinosaurRegistry.VENOM.get(), spitter, level);
    }

    @Override protected void defineSynchedData() {}

    @Override public void tick() {
        super.tick();
        if (level.isClientSide) {
            for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.ASH,
                    getX() + (random.nextDouble() - .5) * .35, getY() + (random.nextDouble() - .5) * .35,
                    getZ() + (random.nextDouble() - .5) * .35, 0, 0, 0);
        } else if (tickCount > 100) discard();
    }

    @Override protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        return !(getOwner() instanceof DinosaurEntity owner && target instanceof DinosaurEntity dinosaur
                && dinosaur.species() == Species.DILOPHOSAURUS && owner.getTarget() != target);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level.isClientSide && getOwner() instanceof DinosaurEntity owner && hit.getEntity() instanceof LivingEntity target
                && target != owner && canHitEntity(target)) {
            target.hurt(DamageSource.thrown(this, owner), 4);
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 1, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 1, false, false));
            discard();
        }
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level.isClientSide) discard();
    }
}
