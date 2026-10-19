package ports.jurassicraft.scanner;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DinoScannerRegistry {
    public static final Supplier<Item> SCANNER = Platform.register(Registry.ITEM_REGISTRY, "dino_scanner",
            () -> new DinoScannerItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    private DinoScannerRegistry() {}
    public static void init() { }
}
