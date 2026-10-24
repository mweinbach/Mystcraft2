/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import org.jetbrains.annotations.Nullable;
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
import ports.projecte.lib.common.crafting.CraftingHelper;
import ports.projecte.lib.common.crafting.conditions.ICondition;

/**
 * Skips data files whose Forge {@code conditions} fail, as Forge does for recipes and advancements.
 * <p>
 * {@code prepare} runs before tags are bound, and {@link ICondition.IContext#EMPTY} reports every tag as empty.
 * A {@code forge:tag_empty} condition, including one nested under {@code forge:not}, is left in place so the file's
 * Fabric load conditions can decide. A condition type this port does not register is another mod's, and is left too.
 * <p>
 * Forge's wrapper formats are unwrapped first: a {@code forge:conditional} recipe and an advancement with an
 * {@code advancements} list become their first entry whose conditions pass, or are dropped when none does.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {

    @Unique
    private static final Logger projecte$LOGGER = LoggerFactory.getLogger("ProjectE");

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
          at = @At("RETURN"))
    private void projecte$applyForgeConditions(ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> cir) {
        cir.getReturnValue().entrySet().removeIf(entry -> {
            JsonElement unwrapped = projecte$unwrapConditional(entry.getKey(), entry.getValue());
            if (unwrapped == null) {
                return true;
            }
            entry.setValue(unwrapped);
            if (!entry.getValue().isJsonObject() || !entry.getValue().getAsJsonObject().has("conditions")) {
                return false;
            }
            JsonElement conditions = entry.getValue().getAsJsonObject().get("conditions");
            if (!conditions.isJsonArray() || projecte$leaveForLater(conditions.getAsJsonArray())) {
                return false;
            }
            try {
                return !CraftingHelper.processConditions(conditions.getAsJsonArray(), ICondition.IContext.EMPTY);
            } catch (JsonParseException e) {
                projecte$LOGGER.debug("Leaving {}: Forge conditions the port cannot evaluate", entry.getKey(), e);
                return false;
            }
        });
    }

    /**
     * The file a Forge wrapper selects: the first {@code recipes} entry of a {@code forge:conditional} recipe, or the
     * first {@code advancements} entry of a conditional advancement, whose conditions pass. Null when none passes.
     * Files without a wrapper are returned unchanged.
     */
    @Unique
    @Nullable
    private static JsonElement projecte$unwrapConditional(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            return element;
        }
        JsonObject object = element.getAsJsonObject();
        String listKey;
        String valueKey;
        if (object.has("type") && object.get("type").isJsonPrimitive() && "forge:conditional".equals(object.get("type").getAsString())) {
            listKey = "recipes";
            valueKey = "recipe";
        } else if (!object.has("criteria") && object.has("advancements") && object.get("advancements").isJsonArray()) {
            listKey = "advancements";
            valueKey = "advancement";
        } else {
            return element;
        }
        if (!object.has(listKey) || !object.get(listKey).isJsonArray()) {
            return element;
        }
        for (JsonElement candidate : object.getAsJsonArray(listKey)) {
            if (!candidate.isJsonObject() || !candidate.getAsJsonObject().has(valueKey)) {
                continue;
            }
            JsonObject entry = candidate.getAsJsonObject();
            JsonElement conditions = entry.get("conditions");
            try {
                if (conditions == null || conditions.isJsonArray() && !projecte$leaveForLater(conditions.getAsJsonArray())
                      && CraftingHelper.processConditions(conditions.getAsJsonArray(), ICondition.IContext.EMPTY)) {
                    return entry.get(valueKey);
                }
            } catch (JsonParseException e) {
                projecte$LOGGER.debug("Skipping an entry of {}: Forge conditions the port cannot evaluate", id, e);
            }
        }
        return null;
    }

    /** True when this pass cannot decide the condition, so the file must stay. */
    @Unique
    private static boolean projecte$leaveForLater(JsonArray conditions) {
        for (JsonElement element : conditions) {
            if (projecte$leaveForLater(element)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean projecte$leaveForLater(JsonElement element) {
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
        if (object.has("value") && projecte$leaveForLater(object.get("value"))) {
            return true;
        }
        return object.has("values") && object.get("values").isJsonArray()
                && projecte$leaveForLater(object.get("values").getAsJsonArray());
    }
}
