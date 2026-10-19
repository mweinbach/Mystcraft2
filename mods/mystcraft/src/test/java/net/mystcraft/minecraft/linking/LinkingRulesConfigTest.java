package net.mystcraft.minecraft.linking;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LinkingRulesConfigTest {
    @Test void sourceDefaultsAndModernHomeKey(){var defaults=LinkingRulesConfig.parse(JsonParser.parseString("{}").getAsJsonObject());assertTrue(defaults.respawnInAges());assertFalse(defaults.requireUUID());assertEquals("minecraft:overworld",defaults.homeDimension().toString());}
    @Test void rejectMalformedConfiguration(){for(String json:java.util.List.of("{\"requireUUID\":\"true\"}","{\"respawnInAges\":0}","{\"homeDimension\":0}","{\"homeDimension\":\"Not Valid\"}","{\"requireUuid\":true}"))assertThrows(IllegalArgumentException.class,()->LinkingRulesConfig.parse(JsonParser.parseString(json).getAsJsonObject()));}
}
