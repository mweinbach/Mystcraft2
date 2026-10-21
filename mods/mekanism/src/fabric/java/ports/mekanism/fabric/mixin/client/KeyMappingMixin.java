/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.extensions.IForgeKeyMapping;

/**
 * Vanilla keeps one mapping per physical key. Forge notifies every mapping bound to that key, so a plain binding and
 * a modified one both see the press. {@link IForgeKeyMapping#isDown()} still requires the modifier and conflict context.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IForgeKeyMapping {

    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void mekanism$setEveryBinding(InputConstants.Key key, boolean held, CallbackInfo ci) {
        for (KeyMapping mapping : ALL.values()) {
            if (key.equals(((IForgeKeyMapping) mapping).getKey())) {
                mapping.setDown(held);
            }
        }
        ci.cancel();
    }
}
