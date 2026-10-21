package mekanism.additions.common.config;

import mekanism.common.config.BaseMekanismConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig.Type;

public class AdditionsClientConfig extends BaseMekanismConfig {

    private final ForgeConfigSpec configSpec;

    AdditionsClientConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Mekanism Additions Client Config. This config only exists on the client.").push("additions-client");
        builder.pop();
        configSpec = builder.build();
    }

    @Override
    public String getFileName() {
        return "additions-client";
    }

    @Override
    public ForgeConfigSpec getConfigSpec() {
        return configSpec;
    }

    @Override
    public Type getConfigType() {
        return Type.CLIENT;
    }
}
