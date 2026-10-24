package ports.gridwright.registry;

import ports.gridwright.machine.MachineMenu;
import ports.gridwright.platform.Platform;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public final class ModMenus {

    public static final Supplier<MenuType<MachineMenu>> MACHINE = Platform.menu("machine", MachineMenu::client);

    private ModMenus() { }

    public static void init() { }
}
