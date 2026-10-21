/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.common.extensions.IForgeLevel;

/**
 * Holds the state Forge adds to levels: whether block snapshots are being restored, and the block entities whose
 * {@code onLoad} is still to run.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements IForgeLevel {

    @Unique
    private boolean mekanism$restoringBlockSnapshots;
    @Unique
    private final List<BlockEntity> mekanism$freshBlockEntities = new ArrayList<>();
    @Unique
    private boolean mekanism$loadingFreshBlockEntities;

    @Override
    public boolean isRestoringBlockSnapshots() {
        return mekanism$restoringBlockSnapshots;
    }

    @Override
    public void setRestoringBlockSnapshots(boolean restoring) {
        mekanism$restoringBlockSnapshots = restoring;
    }

    @Override
    public void addFreshBlockEntities(Collection<BlockEntity> blockEntities) {
        mekanism$freshBlockEntities.addAll(blockEntities);
    }

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void mekanism$loadFreshBlockEntities(CallbackInfo ci) {
        if (mekanism$loadingFreshBlockEntities || mekanism$freshBlockEntities.isEmpty()) {
            return;
        }
        //Block entities queued while these load wait for the next tick, as on Forge
        List<BlockEntity> fresh = List.copyOf(mekanism$freshBlockEntities);
        mekanism$freshBlockEntities.clear();
        mekanism$loadingFreshBlockEntities = true;
        try {
            fresh.forEach(BlockEntity::onLoad);
        } finally {
            mekanism$loadingFreshBlockEntities = false;
        }
    }
}
