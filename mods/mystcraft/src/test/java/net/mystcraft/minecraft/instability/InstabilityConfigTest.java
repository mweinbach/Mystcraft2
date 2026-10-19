package net.mystcraft.minecraft.instability;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InstabilityConfigTest {
    private InstabilityConfig parse(String json){return InstabilityConfig.parse(JsonParser.parseString(json).getAsJsonObject());}
    @Test void sourceDefaultsAndExplicitDisabledConfiguration(){
        var defaults=parse("{}");assertTrue(defaults.globalEnabled());assertEquals(2,defaults.difficulty());assertNull(defaults.baseline());
        var disabled=parse("{\"globalEnabled\":false,\"difficulty\":0,\"baseline\":{}}");assertFalse(disabled.globalEnabled());assertEquals(java.util.Map.of(),disabled.baseline());
    }
    @Test void malformedOperatorsCannotSilentlyEnableOrTruncateScores(){
        for(String json:java.util.List.of("{\"globalEnabled\":\"false\"}","{\"difficulty\":1.5}","{\"difficulty\":4}","{\"difficulty\":2147483648}","{\"difficulty\":\"2\"}","{\"baseline\":[]}","{\"difficultyTypo\":2}"))
            assertThrows(IllegalArgumentException.class,()->parse(json),json);
    }
}
