/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

import org.apache.commons.lang3.tuple.Pair;
import ports.extremereactors.corelib.config.ConfigSpec;
import ports.extremereactors.platform.Platform;

/**
 * Client options for the machine effects, read from {@code config/extremereactors/effects.toml} when the client
 * starts. Every effect is cosmetic and can be turned off on its own.
 */
public final class EffectsConfig {

    public static final EffectsConfig CLIENT;

    public final ConfigSpec.BooleanValue enabled;
    public final ConfigSpec.IntValue range;
    public final ConfigSpec.BooleanValue rodDrives;
    public final ConfigSpec.BooleanValue cherenkovGlow;
    public final ConfigSpec.DoubleValue glowStrength;
    public final ConfigSpec.BooleanValue steam;
    public final ConfigSpec.DoubleValue particleDensity;
    public final ConfigSpec.BooleanValue alarmLamps;
    public final ConfigSpec.BooleanValue alarmSounds;
    public final ConfigSpec.DoubleValue alarmVolume;
    public final ConfigSpec.BooleanValue scram;

    public static void load() {
        SPEC.load(Platform.configDirectory().resolve("extremereactors/effects.toml"));
    }

    public boolean isOn(final ConfigSpec.BooleanValue effect) {
        return this.enabled.get() && effect.get();
    }

    private EffectsConfig(final ConfigSpec.Builder builder) {

        builder.comment("Cosmetic machine effects. They change nothing in how machines run.").push("effects");

        this.enabled = builder
                .comment("If false, turns every effect below off.")
                .define("enabled", true);
        this.range = builder
                .comment("Machines farther than this many blocks show no effects.")
                .defineInRange("range", 64, 16, 256);
        this.rodDrives = builder
                .comment("Draw a drive on each control rod that rises and falls with its insertion.")
                .define("rodDrives", true);
        this.cherenkovGlow = builder
                .comment("Glow blue around the fuel columns of a running reactor, seen through its glass.")
                .define("cherenkovGlow", true);
        this.glowStrength = builder
                .comment("Brightness of the Cherenkov glow.")
                .defineInRange("glowStrength", 1.0, 0.0, 2.0);
        this.steam = builder
                .comment("Steam from the hot fluid ports of running reactors and from venting turbines.")
                .define("steam", true);
        this.particleDensity = builder
                .comment("Scales how many steam particles are spawned.")
                .defineInRange("particleDensity", 1.0, 0.0, 4.0);
        this.alarmLamps = builder
                .comment("Flash a lamp on reactor controllers: red when the fuel reaches 2000 C or a running",
                        "reactor's coolant tank is under 10% full, amber when out of fuel or 90% full of waste.")
                .define("alarmLamps", true);
        this.alarmSounds = builder
                .comment("Sound an alarm while a reactor overheats or runs short of coolant, and a klaxon on SCRAM.")
                .define("alarmSounds", true);
        this.alarmVolume = builder
                .comment("Volume of the alarm and the klaxon, on top of the Blocks volume slider.")
                .defineInRange("alarmVolume", 0.6, 0.0, 1.0);
        this.scram = builder
                .comment("When a reactor is SCRAMmed, drop its rods, flash its lamps and vent steam.")
                .define("scram", true);

        builder.pop();
    }

    private static final ConfigSpec SPEC;

    static {

        final Pair<EffectsConfig, ConfigSpec> pair = new ConfigSpec.Builder().configure(EffectsConfig::new);

        CLIENT = pair.getLeft();
        SPEC = pair.getRight();
    }
}
