/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Arrays;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge tints a beacon beam with {@code getBeaconColorMultiplier} before the stained-glass check. Transparent plastic
 * is not a {@link BeaconBeamBlock}, so the beam reads that color through a stained-glass stand-in.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    @Unique
    private static final ThreadLocal<float[]> projecte$beamColor = new ThreadLocal<>();

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;"))
    private static Block projecte$beaconBlock(BlockState state, Operation<Block> original) {
        Block block = original.call(state);
        if (block instanceof BeaconBeamBlock) {
            projecte$beamColor.remove();
            return block;
        }
        float[] color = state.getBeaconColorMultiplier(null, null, null);
        if (color == null) {
            projecte$beamColor.remove();
            return block;
        }
        projecte$beamColor.set(Arrays.copyOf(color, color.length));
        return Blocks.WHITE_STAINED_GLASS;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColors()[F"))
    private static float[] projecte$beaconColor(DyeColor dye, Operation<float[]> original) {
        float[] color = projecte$beamColor.get();
        projecte$beamColor.remove();
        return color != null ? color : original.call(dye);
    }
}
