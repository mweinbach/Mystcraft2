package ports.jurassicraft.compat.emi;

import java.util.Map;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ports.jurassicraft.assembly.client.SkeletonAssemblyScreen;
import ports.jurassicraft.bugs.BugCrateScreen;
import ports.jurassicraft.cleaning.client.CleaningStationScreen;
import ports.jurassicraft.compat.emi.RecipePreviews.Kind;
import ports.jurassicraft.cultivator.CultivatorScreen;
import ports.jurassicraft.grinder.FossilGrinderScreen;
import ports.jurassicraft.hatching.HatcheryScreen;
import ports.jurassicraft.machine.DnaMachineScreen;

/** Links the existing progress arrows to the matching EMI category without covering slots. */
final class EmiMachineScreens {
    private static volatile Map<Kind, EmiRecipeCategory> categories = Map.of();
    private static boolean registered;
    private EmiMachineScreens() {}

    static void register(Map<Kind, EmiRecipeCategory> current) {
        categories = Map.copyOf(current);
        if (registered) return;
        registered = true;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!hasMachineScreen(screen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((clicked, x, y, button) -> {
                Kind kind = kind(clicked);
                if (button != 0 || kind == null || !hit(clicked, kind, x, y)) return true;
                EmiRecipeCategory category = categories.get(kind);
                if (category == null) return true;
                EmiApi.displayRecipeCategory(category);
                return false;
            });
            ScreenEvents.afterRender(screen).register((rendered, pose, x, y, partial) -> {
                Kind kind = kind(rendered);
                if (kind != null && categories.containsKey(kind) && hit(rendered, kind, x, y))
                    rendered.renderTooltip(pose, Component.translatable("emi.view_all_recipes"), x, y);
            });
        });
    }
    private static boolean hasMachineScreen(Screen screen) {
        return screen instanceof FossilGrinderScreen || screen instanceof CleaningStationScreen
                || screen instanceof SkeletonAssemblyScreen || screen instanceof BugCrateScreen
                || screen instanceof CultivatorScreen || screen instanceof HatcheryScreen || screen instanceof DnaMachineScreen;
    }
    private static Kind kind(Screen screen) {
        if (screen instanceof FossilGrinderScreen) return Kind.GRINDING;
        if (screen instanceof CleaningStationScreen) return Kind.CLEANING;
        if (screen instanceof SkeletonAssemblyScreen) return Kind.SKELETON;
        if (screen instanceof BugCrateScreen) return Kind.BREEDING;
        if (screen instanceof CultivatorScreen) return Kind.CULTIVATING;
        if (screen instanceof HatcheryScreen hatchery) return EmiRouting.hatchery(hatchery.getMenu().machine());
        if (screen instanceof DnaMachineScreen dna) return EmiRouting.dna(dna.getMenu().kind(), dna.getMenu().hybridizerMode());
        return null;
    }
    private static boolean hit(Screen screen, Kind kind, double x, double y) {
        int guiWidth = screen instanceof CultivatorScreen ? 352 : 176;
        int guiHeight = screen instanceof CultivatorScreen ? 188 : screen instanceof SkeletonAssemblyScreen ? 201 : 166;
        return EmiRouting.hit(kind, x - (screen.width - guiWidth) / 2, y - (screen.height - guiHeight) / 2);
    }
}
