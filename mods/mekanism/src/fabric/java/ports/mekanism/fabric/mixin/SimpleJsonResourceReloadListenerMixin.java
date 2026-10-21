/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.crafting.CraftingHelper;
import ports.mekanism.lib.common.crafting.conditions.ICondition;

/**
 * Skips data files whose Forge {@code conditions} fail, as Forge does for recipes and advancements.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {

    @Unique
    private static final Logger mekanism$LOGGER = LoggerFactory.getLogger("Mekanism");

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
          at = @At("RETURN"))
    private void mekanism$applyForgeConditions(ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> cir) {
        cir.getReturnValue().entrySet().removeIf(entry -> {
            if (!entry.getValue().isJsonObject() || !entry.getValue().getAsJsonObject().has("conditions")) {
                return false;
            }
            try {
                return !CraftingHelper.processConditions(entry.getValue().getAsJsonObject(), "conditions", ICondition.IContext.EMPTY);
            } catch (JsonParseException e) {
                mekanism$LOGGER.debug("Skipping {}: unreadable Forge conditions", entry.getKey(), e);
                return true;
            }
        });
    }
}
