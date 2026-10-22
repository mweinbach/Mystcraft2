package mekanism.client.jei;

import mekanism.client.gui.GuiMekanism;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * Marks a Mekanism screen that JEI is about to replace, so closing it does not drop the open container.
 * Only registered when JEI is loaded, so this class is not initialized otherwise.
 */
public final class JeiScreenWatcher {

    private JeiScreenWatcher() {
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getCurrentScreen() instanceof GuiMekanism<?> screen && event.getNewScreen() instanceof IRecipesGui) {
            screen.switchingToJEI = true;
        }
    }
}
