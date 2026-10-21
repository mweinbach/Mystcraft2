/*
 * Mekanism 1.19.2 port - empty Additions module scaffold.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.additions.common;

import mekanism.common.Mekanism;
import mekanism.common.base.IModModule;
import mekanism.common.lib.Version;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/**
 * Empty scaffold for the {@code mekanismadditions} jar. The additions import replaces this class.
 */
@Mod(MekanismAdditions.MODID)
public class MekanismAdditions implements IModModule {

    public static final String MODID = "mekanismadditions";

    public static MekanismAdditions instance;

    public final Version versionNumber;

    public MekanismAdditions() {
        Mekanism.addModule(instance = this);
        versionNumber = new Version(ModLoadingContext.get().getActiveContainer());
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }

    @Override
    public String getName() {
        return "Additions";
    }

    @Override
    public void resetClient() {
    }
}
