/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.extensions.IForgeLevel;

/**
 * Holds the state Forge adds to levels: whether block snapshots are being restored, and the block entities whose
 * {@code projecte$onLoad} is still to run.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements IForgeLevel {

    @Unique
    private boolean projecte$restoringBlockSnapshots;
    @Unique
    private final List<BlockEntity> projecte$freshBlockEntities = new ArrayList<>();
    @Unique
    private boolean projecte$loadingFreshBlockEntities;

    @Override
    public boolean projecte$isRestoringBlockSnapshots() {
        return projecte$restoringBlockSnapshots;
    }

    @Override
    public void projecte$setRestoringBlockSnapshots(boolean restoring) {
        projecte$restoringBlockSnapshots = restoring;
    }

    @Override
    public void projecte$addFreshBlockEntities(Collection<BlockEntity> blockEntities) {
        projecte$freshBlockEntities.addAll(blockEntities);
    }

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void projecte$loadFreshBlockEntities(CallbackInfo ci) {
        if (projecte$loadingFreshBlockEntities || projecte$freshBlockEntities.isEmpty()) {
            return;
        }
        //Block entities queued while these load wait for the next tick, as on Forge
        List<BlockEntity> fresh = List.copyOf(projecte$freshBlockEntities);
        projecte$freshBlockEntities.clear();
        projecte$loadingFreshBlockEntities = true;
        try {
            fresh.forEach(BlockEntity::projecte$onLoad);
        } finally {
            projecte$loadingFreshBlockEntities = false;
        }
    }
}
