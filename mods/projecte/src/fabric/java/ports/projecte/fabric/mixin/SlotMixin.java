/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.extensions.IForgeSlot;

/**
 * Stores the background Forge lets any slot set, and shows it when the slot is empty.
 */
@Mixin(Slot.class)
public abstract class SlotMixin implements IForgeSlot {

    @Unique
    @Nullable
    private Pair<ResourceLocation, ResourceLocation> projecte$background;

    @Override
    public Slot projecte$setBackground(ResourceLocation atlas, ResourceLocation sprite) {
        projecte$background = Pair.of(atlas, sprite);
        return (Slot) (Object) this;
    }

    @Inject(method = "getNoItemIcon", at = @At("HEAD"), cancellable = true)
    private void projecte$background(CallbackInfoReturnable<Pair<ResourceLocation, ResourceLocation>> cir) {
        if (projecte$background != null) {
            cir.setReturnValue(projecte$background);
        }
    }
}
