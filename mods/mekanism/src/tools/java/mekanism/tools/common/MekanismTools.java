/*
 * Mekanism 1.19.2 port - empty Tools module scaffold.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.tools.common;

import mekanism.common.Mekanism;
import mekanism.common.base.IModModule;
import mekanism.common.lib.Version;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/**
 * Empty scaffold for the {@code mekanismtools} jar. The tools import replaces this class.
 */
@Mod(MekanismTools.MODID)
public class MekanismTools implements IModModule {

    public static final String MODID = "mekanismtools";

    public static MekanismTools instance;

    public final Version versionNumber;

    public MekanismTools() {
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
        return "Tools";
    }

    @Override
    public void resetClient() {
    }
}
