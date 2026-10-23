package com.technovision.advancedgenetics.forge;

import com.technovision.advancedgenetics.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the shared {@link Config} to a ForgeConfigSpec, so Forge owns loading, validation and reloads.
 * COMMON configs are not synced to clients, on load or on reload.
 */
public final class ForgeConfig {

    private static final Map<Config.Value<?>, ForgeConfigSpec.ConfigValue<?>> BINDINGS = new LinkedHashMap<>();
    private static final ForgeConfigSpec SPEC = build();

    private ForgeConfig() { }

    static void register(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, Config.FILE_NAME);
        modBus.addListener((ModConfigEvent.Loading event) -> onLoad(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onLoad(event.getConfig()));
    }

    private static ForgeConfigSpec build() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        for (Config.Section section : Config.sections()) {
            builder.comment(section.comment().toArray(String[]::new)).push(section.name());
            for (Config.Value<?> value : section.values()) {
                if (!value.comment().isEmpty()) {
                    builder.comment(value.comment().toArray(String[]::new));
                }
                BINDINGS.put(value, define(builder, value));
            }
            builder.pop();
        }
        return builder.build();
    }

    private static ForgeConfigSpec.ConfigValue<?> define(ForgeConfigSpec.Builder builder, Config.Value<?> value) {
        if (value instanceof Config.BoolValue bool) {
            return builder.define(bool.key(), bool.defaultValue().booleanValue());
        }
        if (value instanceof Config.IntValue integer) {
            return builder.defineInRange(integer.key(), integer.defaultValue().intValue(), integer.min(), integer.max());
        }
        if (value instanceof Config.DoubleValue decimal) {
            return builder.defineInRange(decimal.key(), decimal.defaultValue().doubleValue(), decimal.min(), decimal.max());
        }
        throw new IllegalArgumentException("Unsupported config value " + value.key());
    }

    private static void onLoad(ModConfig config) {
        if (config.getSpec() != SPEC) return;
        // BooleanValue returns the raw file value, which may be a "true"/"false" string the spec accepts.
        BINDINGS.forEach((value, bound) -> value.load(bound.get()));
    }

    /** The spec, so the shared GameTests can check its corrections against Fabric's. */
    public static ForgeConfigSpec spec() {
        return SPEC;
    }
}
