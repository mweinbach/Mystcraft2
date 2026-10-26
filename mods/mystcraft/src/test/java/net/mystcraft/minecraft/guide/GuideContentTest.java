package net.mystcraft.minecraft.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideContentTest {
    private static JsonObject json(String path) throws Exception {
        try (var input = new InputStreamReader(GuideContentTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(input).getAsJsonObject();
        }
    }

    @Test void allPagesHaveEnglishTranslationsAndNoPageIsOmitted() throws Exception {
        var lang = json("/assets/mystcraft/lang/en_us.json");
        var pages = GuideContent.pages();
        assertFalse(pages.isEmpty());
        assertEquals(pages.size(), lang.keySet().stream().filter(key -> key.startsWith("book.mystcraft.guide.page.")).count());
        for (int i = 0; i < pages.size(); i++) {
            assertInstanceOf(TranslatableContents.class, pages.get(i).getContents());
            String key = ((TranslatableContents) pages.get(i).getContents()).getKey();
            assertEquals("book.mystcraft.guide.page." + (i + 1), key);
            assertTrue(lang.has(key), key);
            assertFalse(lang.get(key).getAsString().isBlank(), key);
        }
    }

    private static void checkRecipe(JsonObject lang, int page, String recipe, Map<String, String> expectedIngredients,
            Map<String, String> expectedLegends) throws Exception {
        String text = lang.get("book.mystcraft.guide.page." + page).getAsString();
        var pattern = Pattern.compile("(?m)^[A-Z.] [A-Z.] [A-Z.]$").matcher(text);
        var illustrated = new java.util.ArrayList<String>();
        while (pattern.find()) illustrated.add(pattern.group().replace(" ", "").replace('.', ' ').toLowerCase(java.util.Locale.ROOT));
        assertEquals(3, illustrated.size(), recipe);

        var definition = json("/data/mystcraft/recipes/" + recipe + ".json");
        var rows = definition.getAsJsonArray("pattern");
        for (int row = 0; row < 3; row++) {
            String expected = row < rows.size() ? rows.get(row).getAsString() : "";
            assertEquals(String.format("%-3s", expected), illustrated.get(row), recipe + " row " + row);
        }
        var key = definition.getAsJsonObject("key");
        assertEquals(expectedIngredients.keySet(), key.keySet(), recipe);
        for (var ingredient : expectedIngredients.entrySet()) {
            String[] typeAndId = ingredient.getValue().split("=", 2);
            assertEquals(typeAndId[1], key.getAsJsonObject(ingredient.getKey()).get(typeAndId[0]).getAsString(), recipe);
        }
        for (var legend : expectedLegends.entrySet()) {
            assertTrue(text.contains("\n" + legend.getKey() + " = " + legend.getValue()), recipe + " legend " + legend.getKey());
        }
    }

    @Test void illustratedRecipesMatchTheirPatternsAndIngredients() throws Exception {
        var lang = json("/assets/mystcraft/lang/en_us.json");
        checkRecipe(lang, 31, "ink_mixer", Map.of("s", "item=minecraft:stone", "v", "item=minecraft:glass_bottle", "p", "tag=minecraft:planks"),
                Map.of("S", "stone", "V", "glass bottle", "P", "any planks"));
        checkRecipe(lang, 32, "book_binder", Map.of("i", "item=minecraft:iron_ingot", "p", "tag=minecraft:planks"),
                Map.of("I", "iron ingot", "P", "any planks"));
        checkRecipe(lang, 33, "writing_desk", Map.of("v", "item=minecraft:glass_bottle", "f", "item=minecraft:feather", "p", "tag=minecraft:planks"),
                Map.of("V", "glass bottle", "F", "feather", "P", "any planks"));
        checkRecipe(lang, 34, "bookstand", Map.of("s", "tag=mystcraft:wooden_rods", "p", "tag=minecraft:planks"),
                Map.of("S", "stick", "P", "any planks"));
        checkRecipe(lang, 35, "folder", Map.of("l", "item=minecraft:leather", "s", "item=minecraft:string"),
                Map.of("L", "leather", "S", "string"));
        var rods = json("/data/mystcraft/tags/items/wooden_rods.json").getAsJsonArray("values");
        assertTrue(java.util.stream.StreamSupport.stream(rods.spliterator(), false)
                .anyMatch(value -> value.isJsonPrimitive() && value.getAsString().equals("minecraft:stick")));
    }
}
