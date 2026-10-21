/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * <p>
 * {@code prepare} runs before tags are bound, and {@link ICondition.IContext#EMPTY} reports every tag as empty.
 * A {@code forge:tag_empty} condition, including one nested under {@code forge:not}, is left in place so the file's
 * Fabric load conditions can decide. A condition type this port does not register is another mod's, and is left too.
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
            JsonElement conditions = entry.getValue().getAsJsonObject().get("conditions");
            if (!conditions.isJsonArray() || mekanism$leaveForLater(conditions.getAsJsonArray())) {
                return false;
            }
            try {
                return !CraftingHelper.processConditions(conditions.getAsJsonArray(), ICondition.IContext.EMPTY);
            } catch (JsonParseException e) {
                mekanism$LOGGER.debug("Leaving {}: Forge conditions Mekanism cannot evaluate", entry.getKey(), e);
                return false;
            }
        });
    }

    /** True when this pass cannot decide the condition, so the file must stay. */
    @Unique
    private static boolean mekanism$leaveForLater(JsonArray conditions) {
        for (JsonElement element : conditions) {
            if (mekanism$leaveForLater(element)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean mekanism$leaveForLater(JsonElement element) {
        if (!element.isJsonObject()) {
            return true;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) {
            return true;
        }
        String type = object.get("type").getAsString();
        if ("forge:tag_empty".equals(type)) {
            return true;
        }
        try {
            if (!CraftingHelper.isConditionKnown(new ResourceLocation(type))) {
                return true;
            }
        } catch (IllegalArgumentException invalid) {
            return true;
        }
        if (object.has("value") && mekanism$leaveForLater(object.get("value"))) {
            return true;
        }
        return object.has("values") && object.get("values").isJsonArray()
                && mekanism$leaveForLater(object.get("values").getAsJsonArray());
    }
}
