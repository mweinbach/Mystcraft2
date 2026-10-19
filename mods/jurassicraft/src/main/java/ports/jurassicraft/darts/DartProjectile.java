package ports.jurassicraft.darts;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import ports.jurassicraft.entity.DinosaurEntity;

public final class DartProjectile extends ThrowableItemProjectile {
    public DartProjectile(EntityType<? extends DartProjectile> type, Level level) { super(type, level); }
    public DartProjectile(Level level, LivingEntity shooter, ItemStack dart) {
        super(DartRegistry.PROJECTILE.get(), shooter, level);
        ItemStack singleDart = dart.copy();
        singleDart.setCount(1);
        setItem(singleDart);
    }
    @Override protected Item getDefaultItem() { return DartRegistry.TRANQUILIZER.get(); }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level.isClientSide && hit.getEntity() instanceof DinosaurEntity dinosaur && getItem().getItem() instanceof DartItem dart) {
            switch (dart.kind()) {
                case TRANQUILIZER -> dinosaur.tranquilize(2000);
                case POISON -> dinosaur.addEffect(new MobEffectInstance(MobEffects.POISON, 2000));
                case LETHAL -> dinosaur.setDeathIn(200);
                case POTION -> PotionUtils.getMobEffects(getItem()).forEach(effect -> dinosaur.addEffect(new MobEffectInstance(effect)));
            }
        }
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level.isClientSide) discard();
    }
    @Override public Packet<?> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
