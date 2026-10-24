package ports.gridwright.client;

import ports.gridwright.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client-only registration; each loader's client entrypoint calls this at its equivalent point. */
public final class GridwrightClient {

    private GridwrightClient() { }

    /** Registers the machine screen; run on the client main thread. */
    public static void registerScreens() {
        MenuScreens.register(ModMenus.MACHINE.get(), MachineScreen::new);
    }
}
