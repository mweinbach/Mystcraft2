/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge;

import com.google.gson.JsonObject;
import it.zerono.mods.extremereactors.config.conditions.ConfigCondition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * {@link ConfigCondition} as a Forge recipe condition.
 */
record ForgeConfigCondition(String configName) implements ICondition {

    static final IConditionSerializer<ForgeConfigCondition> SERIALIZER = new IConditionSerializer<>() {

        @Override
        public void write(final JsonObject json, final ForgeConfigCondition value) {
            json.addProperty(ConfigCondition.JSON_CONFIG_NAME, value.configName());
        }

        @Override
        public ForgeConfigCondition read(final JsonObject json) {
            return new ForgeConfigCondition(GsonHelper.getAsString(json, ConfigCondition.JSON_CONFIG_NAME));
        }

        @Override
        public ResourceLocation getID() {
            return ConfigCondition.ID;
        }
    };

    @Override
    public ResourceLocation getID() {
        return ConfigCondition.ID;
    }

    @Override
    public boolean test(final IContext context) {
        return ConfigCondition.test(this.configName);
    }
}
