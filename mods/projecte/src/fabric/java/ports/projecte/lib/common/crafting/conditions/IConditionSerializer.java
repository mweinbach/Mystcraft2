/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting.conditions;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

public interface IConditionSerializer<T extends ICondition> {

    void write(JsonObject json, T value);

    T read(JsonObject json);

    ResourceLocation getID();

    default JsonObject getJson(T value) {
        JsonObject json = new JsonObject();
        write(json, value);
        json.addProperty("type", value.getID().toString());
        return json;
    }
}
