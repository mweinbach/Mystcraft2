package ports.gridwright.client;

import net.minecraft.world.inventory.ChestMenu;
import ports.gridwright.registry.ModMenus;
import ports.gridwright.registry.ModUtilities;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

/** Client-only registration; each loader's client entrypoint calls this at its equivalent point. */
public final class GridwrightClient {

    private GridwrightClient() { }

    /** Registers the machine and Bag of Holding screens; run on the client main thread. */
    public static void registerScreens() {
        MenuScreens.register(ModMenus.MACHINE.get(), MachineScreen::new);
        MenuScreens.<ChestMenu, ContainerScreen>register(ModUtilities.BAG_MENU.get(), ContainerScreen::new);
    }
}
