package mekanism.additions.common.config;

import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.value.CachedFloatValue;
import mekanism.common.config.value.CachedIntValue;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig.Type;

public class AdditionsConfig extends BaseMekanismConfig {

    private final ForgeConfigSpec configSpec;

    public final CachedIntValue obsidianTNTDelay;
    public final CachedFloatValue obsidianTNTBlastRadius;

    AdditionsConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Mekanism Additions Config. This config is synced between server and client.").push("additions");

        obsidianTNTDelay = CachedIntValue.wrap(this, builder.comment("Fuse time for Obsidian TNT.")
              .defineInRange("obsidianTNTDelay", 100, 0, Integer.MAX_VALUE));
        obsidianTNTBlastRadius = CachedFloatValue.wrap(this, builder.comment("Radius of the explosion of Obsidian TNT.")
              .defineInRange("obsidianTNTBlastRadius", 12, 0.1, 1_000));

        builder.pop();
        configSpec = builder.build();
    }

    @Override
    public String getFileName() {
        return "additions";
    }

    @Override
    public ForgeConfigSpec getConfigSpec() {
        return configSpec;
    }

    @Override
    public Type getConfigType() {
        return Type.SERVER;
    }
}
