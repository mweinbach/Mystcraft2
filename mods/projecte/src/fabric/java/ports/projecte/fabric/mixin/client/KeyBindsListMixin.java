/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.extensions.IForgeKeyMapping;

/**
 * Marks a binding as conflicting when its key is another binding's modifier. Forge 43.5.0 draws that case in gold and
 * a real key conflict in red; both are drawn as a conflict here.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindsListMixin {

    /** Wrapped, not redirected: JurassiCraft's Fabric port wraps the same call. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"))
    private boolean projecte$modifierConflict(KeyMapping self, KeyMapping other, Operation<Boolean> original) {
        return original.call(self, other) || ((IForgeKeyMapping) other).hasKeyModifierConflict(self);
    }

    /**
     * The per-row reset button restores the default key through {@code Options.setKey}. That leaves a modifier in
     * place, so restore the default modifier first, as Forge does.
     */
    @Inject(method = "method_19870", remap = false, at = @At("HEAD"))
    private void projecte$resetOne(KeyMapping key, Button button, CallbackInfo ci) {
        ((IForgeKeyMapping) key).setToDefault();
    }
}
