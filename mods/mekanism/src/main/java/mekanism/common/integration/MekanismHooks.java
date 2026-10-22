package mekanism.common.integration;

import java.util.List;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Hooks for Mekanism. Use to grab items or blocks out of different mods.
 *
 * @author AidanBrady
 */
public final class MekanismHooks {

    public static final String CC_MOD_ID = "computercraft";
    public static final String CRAFTTWEAKER_MOD_ID = "crafttweaker";
    public static final String CURIOS_MODID = "curios";
    public static final String DARK_MODE_EVERYWHERE_MODID = "darkmodeeverywhere";
    public static final String FLUX_NETWORKS_MOD_ID = "fluxnetworks";
    public static final String IC2_MOD_ID = "ic2";
    public static final String JEI_MOD_ID = "jei";
    public static final String JEITWEAKER_MOD_ID = "jeitweaker";
    public static final String OC2_MOD_ID = "oc2";
    public static final String PROJECTE_MOD_ID = "projecte";
    public static final String RECIPE_STAGES_MOD_ID = "recipestages";
    public static final String TOP_MOD_ID = "theoneprobe";
    public static final String WILDFIRE_GENDER_MOD_ID = "wildfire_gender";

    public boolean CCLoaded;
    public boolean CraftTweakerLoaded;
    public boolean CuriosLoaded;
    public boolean DMELoaded;
    public boolean FluxNetworksLoaded;
    public boolean IC2Loaded;
    public boolean JEILoaded;
    public boolean OC2Loaded;
    public boolean ProjectELoaded;
    public boolean RecipeStagesLoaded;
    public boolean TOPLoaded;
    public boolean WildfireGenderModLoaded;

    public void hookConstructor(final IEventBus bus) {
    }

    public void hookCommonSetup() {
        ModList modList = ModList.get();
        DMELoaded = modList.isLoaded(DARK_MODE_EVERYWHERE_MODID);
        IC2Loaded = modList.isLoaded(IC2_MOD_ID);
        JEILoaded = modList.isLoaded(JEI_MOD_ID);
        TOPLoaded = modList.isLoaded(TOP_MOD_ID);
    }

    public void sendIMCMessages(InterModEnqueueEvent event) {
        if (DMELoaded) {
            //Note: While it is only strings, so it is safe to call and IMC validates the mods are loaded
            // we add this check here, so we can skip iterating the list of things we want to blacklist when it is not present
            sendDarkModeEverywhereIMC();
        }
        if (TOPLoaded) {
            // The provider class is Forge-only. Resolving it by name keeps this shared class free of The One Probe
            // types, so Fabric still compiles and loads when that mod is absent.
            InterModComms.sendTo(TOP_MOD_ID, "getTheOneProbe", MekanismHooks::topProbe);
        }
    }

    private static Object topProbe() {
        try {
            return Class.forName("mekanism.common.integration.lookingat.theoneprobe.TOPProvider").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The One Probe is loaded but Mekanism's provider failed to load", e);
        }
    }

    public boolean computerCompatEnabled() {
        return CCLoaded || OC2Loaded;
    }

    /**
     * @apiNote DME only uses strings in IMC, so we can safely just include them here without worrying about classloading issues
     */
    private void sendDarkModeEverywhereIMC() {
        List<String> methodBlacklist = List.of(
              //Used for drawing fluids and chemicals in various GUIs including JEI as well as similar styled things
              "mekanism.client.gui.GuiUtils:drawTiledSprite",
              //MekaSuit HUD rendering (already configurable by the user)
              "mekanism.client.render.HUDRenderer:renderCompass",
              "mekanism.client.render.HUDRenderer:renderHUDElement"
        );
        for (String method : methodBlacklist) {
            InterModComms.sendTo(DARK_MODE_EVERYWHERE_MODID, "dme-shaderblacklist", () -> method);
        }
    }
}