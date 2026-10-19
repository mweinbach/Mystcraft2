package ports.jurassicraft.scanner;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DinoScannerRegistry {
    private static Item scanner;
    public static final Supplier<Item> SCANNER = () -> Objects.requireNonNull(scanner, "Scanner registry not initialized");
    private DinoScannerRegistry() {}
    public static void init() {
        scanner = Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, "dino_scanner"),
                new DinoScannerItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    }
}
