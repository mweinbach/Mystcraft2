package mekanism.common.integration;

import java.util.List;
import mekanism.common.integration.computer.computercraft.CCCapabilityHelper;
import mekanism.common.integration.curios.CuriosIntegration;
import mekanism.common.integration.gender.GenderCapabilityHelper;
import mekanism.common.integration.recipestages.RecipeStagesCompat;
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
    public static final String TRINKETS_MODID = "trinkets";
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
        CraftTweakerLoaded = ModList.get().isLoaded(CRAFTTWEAKER_MOD_ID);
        if (CraftTweakerLoaded) {
            registerCrTContent(bus);
        }
        // Curios on Forge, Trinkets on Fabric. One flag: the shared lookups behave the same for either mod.
        // Listeners have to be registered during construction. Client setup can run alongside common setup.
        CuriosLoaded = ModList.get().isLoaded(CURIOS_MODID) || ModList.get().isLoaded(TRINKETS_MODID);
        if (CuriosLoaded) {
            CuriosIntegration.addListeners(bus);
            CuriosIntegration.installNative();
        }
        WildfireGenderModLoaded = ModList.get().isLoaded(WILDFIRE_GENDER_MOD_ID);
    }

    public void hookCommonSetup() {
        ModList modList = ModList.get();
        CCLoaded = modList.isLoaded(CC_MOD_ID);
        DMELoaded = modList.isLoaded(DARK_MODE_EVERYWHERE_MODID);
        IC2Loaded = modList.isLoaded(IC2_MOD_ID);
        JEILoaded = modList.isLoaded(JEI_MOD_ID);
        ProjectELoaded = modList.isLoaded(PROJECTE_MOD_ID);
        RecipeStagesLoaded = modList.isLoaded(RECIPE_STAGES_MOD_ID);
        TOPLoaded = modList.isLoaded(TOP_MOD_ID);
        FluxNetworksLoaded = modList.isLoaded(FLUX_NETWORKS_MOD_ID);
        if (RecipeStagesLoaded) {
            // Recipe Stages has only Forge builds. Its types stay in the Forge source set behind this shared facade.
            RecipeStagesCompat.load();
        }
        if (WildfireGenderModLoaded) {
            // Fabric registers with the Gender Mod here. Forge attaches the capability when a stack is created;
            // this also loads that adapter.
            GenderCapabilityHelper.registerCore();
        }
        if (CCLoaded) {
            CCCapabilityHelper.registerCCMathHelper();
            // Forge exposes the peripheral as a capability. Fabric's provider is a separate class so this shared
            // hook does not mention its types.
            registerFabricPeripherals();
        }
    }

    private static void registerCrTContent(IEventBus bus) {
        // Custom chemicals and robit skins come from #loader mekanismcontent scripts, which run inside Mekanism's first
        // registry event. The listener goes on CraftTweaker's own mod bus, a Forge type, so the Forge-only class is
        // resolved by name. Fabric has no such hook; see parity/19-crafttweaker.md.
        try {
            Class.forName("mekanism.common.integration.crafttweaker.content.ForgeCrTContentRegistration")
                  .getMethod("register", IEventBus.class).invoke(null, bus);
        } catch (ClassNotFoundException ignored) {
            // The Fabric JAR does not register CraftTweaker content.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CraftTweaker is loaded but Mekanism's content registration failed to load", e);
        }
    }

    private static void registerFabricPeripherals() {
        try {
            Class.forName("mekanism.common.integration.computer.computercraft.FabricPeripheralProvider")
                  .getMethod("register").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // The Forge JAR attaches the peripheral capability and does not contain the Fabric provider.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ComputerCraft is loaded but Mekanism's peripheral provider failed to load", e);
        }
    }

    public void sendIMCMessages(InterModEnqueueEvent event) {
        if (DMELoaded) {
            //Note: While it is only strings, so it is safe to call and IMC validates the mods are loaded
            // we add this check here, so we can skip iterating the list of things we want to blacklist when it is not present
            sendDarkModeEverywhereIMC();
        }
        if (CuriosLoaded) {
            CuriosIntegration.sendIMC();
        }
        if (TOPLoaded) {
            // The provider class is Forge-only. Resolving it by name keeps this shared class free of The One Probe
            // types, so Fabric still compiles and loads when that mod is absent.
            InterModComms.sendTo(TOP_MOD_ID, "getTheOneProbe", MekanismHooks::topProbe);
        }
        if (ProjectELoaded) {
            // NSS serializers and the recipe mappers are Forge-only. Resolving the helper by name keeps this shared
            // class free of ProjectE types. Fabric has no ProjectE mapper API.
            registerProjectESerializers();
        }
    }

    private static void registerProjectESerializers() {
        try {
            Class.forName("mekanism.common.integration.projecte.NSSHelper").getMethod("init").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // The Fabric JAR does not contain the ProjectE mappers.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ProjectE is loaded but Mekanism's NSS serializers failed to load", e);
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