package mekanism.client.jei;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JEI category coverage")
class JeiRecipeTypeCoverageTest {

    private static final Pattern REGISTERED = Pattern.compile("register\\(\"([a-z0-9_]+)\"");

    @Test
    @DisplayName("Every Mekanism recipe type maps to a registered JEI category")
    void everyRecipeTypeHasACategory() throws IOException {
        String source = Files.readString(recipeTypeSource());
        Set<String> recipeTypes = new LinkedHashSet<>();
        Matcher matcher = REGISTERED.matcher(source);
        while (matcher.find()) {
            recipeTypes.add(matcher.group(1));
        }
        Assertions.assertFalse(recipeTypes.isEmpty(), "no recipe types found in MekanismRecipeType");
        Assertions.assertEquals(recipeTypes, JeiRecipeTypeCoverage.coveredRecipeTypeNames());
    }

    private static Path recipeTypeSource() {
        Path relative = Path.of("src/main/java/mekanism/common/recipe/MekanismRecipeType.java");
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError("MekanismRecipeType.java not found from " + Path.of("").toAbsolutePath());
    }
}
