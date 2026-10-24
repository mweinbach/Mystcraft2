/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.AddReloadListenerEvent;

/**
 * Forge posts {@link AddReloadListenerEvent} while {@code loadResources} is assembling its listener list, then reloads
 * vanilla's listeners followed by whatever the event added.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {

    @WrapOperation(method = "loadResources", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/ReloadableServerResources;listeners()Ljava/util/List;"))
    private static List<PreparableReloadListener> projecte$reloadListeners(ReloadableServerResources resources,
          Operation<List<PreparableReloadListener>> original) {
        List<PreparableReloadListener> listeners = new ArrayList<>(original.call(resources));
        AddReloadListenerEvent event = new AddReloadListenerEvent(resources);
        MinecraftForge.EVENT_BUS.post(event);
        listeners.addAll(event.getListeners());
        return listeners;
    }
}
