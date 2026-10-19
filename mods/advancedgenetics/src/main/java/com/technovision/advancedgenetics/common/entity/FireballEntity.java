package com.technovision.advancedgenetics.common.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FireballEntity extends SmallFireball {

    public FireballEntity(Level world, Player player, Vec3 v3) {
        super(world, player.getX(), player.getY() + player.getEyeHeight(), player.getZ(), v3.x(), v3.y(), v3.z());
        this.setOwner(player);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level.isClientSide) {
            boolean bl = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            this.level.explode(this, this.getX(), this.getY(), this.getZ(), 1, bl, bl ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.NONE);
            this.discard();
        }
    }
}
