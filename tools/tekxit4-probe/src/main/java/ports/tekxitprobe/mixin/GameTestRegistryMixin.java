package ports.tekxitprobe.mixin;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The full pack registers well over a thousand GameTests from other mods. When
 * {@code -Dtekxitprobe.tests=<simple class name>,...} is set, the headless GameTest server only
 * runs those classes (matched like vanilla's {@code /test runall <class>}: lower-cased simple name).
 */
@Mixin(GameTestRegistry.class)
public abstract class GameTestRegistryMixin {
    @Inject(method = "getAllTestFunctions", at = @At("RETURN"), cancellable = true)
    private static void tekxitprobe$filter(CallbackInfoReturnable<Collection<TestFunction>> cir) {
        String filter = System.getProperty("tekxitprobe.tests");
        if (filter == null || filter.isBlank()) {
            return;
        }
        List<String> classes = Arrays.stream(filter.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT) + ".")
                .filter(s -> s.length() > 1)
                .toList();
        cir.setReturnValue(cir.getReturnValue().stream()
                .filter(test -> classes.stream().anyMatch(test.getTestName()::startsWith))
                .collect(Collectors.toList()));
    }
}
