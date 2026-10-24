package ports.gridwright.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;

/**
 * The client's recipe manager for the viewer plugins. Kept in its own class so the plugins themselves
 * never name a client-only type and stay loadable (and testable) on a dedicated server.
 */
public final class ClientRecipes {

    private ClientRecipes() { }

    @Nullable
    public static RecipeManager manager() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getRecipeManager();
    }
}
