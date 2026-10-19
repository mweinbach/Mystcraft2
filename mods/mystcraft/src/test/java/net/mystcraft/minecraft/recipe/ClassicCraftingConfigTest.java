package net.mystcraft.minecraft.recipe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassicCraftingConfigTest {
    @Test void sourceDefaultAndDisable() {
        assertTrue(ClassicCraftingConfig.parse(JsonParser.parseString("{}").getAsJsonObject()));
        assertFalse(ClassicCraftingConfig.parse(JsonParser.parseString("{\"crafting.linkbook.enabled\":false}").getAsJsonObject()));
    }
    @Test void malformedCannotSilentlyEnableCrafting() {
        for (String json : java.util.List.of("{\"crafting.linkbook.enabled\":1}", "{\"crafting.linkbook.enabled\":\"false\"}", "{\"linkbook\":false}")) {
            assertThrows(IllegalArgumentException.class, () -> ClassicCraftingConfig.parse(JsonParser.parseString(json).getAsJsonObject()));
        }
    }
}
