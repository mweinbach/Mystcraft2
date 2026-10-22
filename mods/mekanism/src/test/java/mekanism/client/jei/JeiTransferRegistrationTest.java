package mekanism.client.jei;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The transfer handlers reference JEI, which is not on the test runtime classpath. This checks the registration
 * calls are present without loading those classes.
 */
@DisplayName("JEI recipe transfer registration")
class JeiTransferRegistrationTest {

    @Test
    @DisplayName("Crafting grids and ghost slots are registered with JEI")
    void transferHandlersAreRegistered() throws IOException {
        String source = Files.readString(pluginSource());
        String transfer = method(source, "registerRecipeTransferHandlers");
        Assertions.assertTrue(transfer.contains("CraftingRobitContainer.class"), transfer);
        Assertions.assertTrue(transfer.contains("new FormulaicRecipeTransferInfo()"), transfer);
        Assertions.assertTrue(transfer.contains("QIODashboardContainer.class"), transfer);
        Assertions.assertTrue(transfer.contains("PortableQIODashboardContainer.class"), transfer);
        String gui = method(source, "registerGuiHandlers");
        Assertions.assertTrue(gui.contains("new GhostIngredientHandler<>()"), gui);
    }

    private static String method(String source, String name) {
        int start = source.indexOf("void " + name);
        Assertions.assertTrue(start >= 0, name);
        int next = source.indexOf("\n    public ", start + 1);
        int end = next < 0 ? source.length() : next;
        return source.substring(start, end);
    }

    private static Path pluginSource() {
        Path relative = Path.of("src/main/java/mekanism/client/jei/MekanismJEI.java");
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError("MekanismJEI.java not found from " + Path.of("").toAbsolutePath());
    }
}
