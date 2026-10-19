package net.mystcraft.minecraft.writing;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SymbolTuningTest {
    @Test void unknownRegisteredFluidUsesOriginalOrdinaryDefaultsNotGuessedOreOrOilAliases() {
        var rule = SymbolTuning.legacyDefault("fluid.some_new_mod.rare_oil");
        assertEquals(1, rule.accessible()); assertEquals(.25F, rule.total());
        assertEquals(4, rule.grammarRank()); assertEquals(4, rule.cardRank()); assertFalse(rule.seaBanned());
        assertEquals(1, SymbolTuning.legacyDefault("fluid.oil").accessible());
        assertEquals(0, SymbolTuning.legacyDefault("fluid.oil").total());
        assertEquals(-6, SymbolTuning.legacyDefault("fluid.mana").accessible());
        assertEquals(-2, SymbolTuning.legacyDefault("fluid.mana").total());
        assertEquals(0, SymbolTuning.legacyDefault("fluid.myst.ink.black").total());
        assertTrue(SymbolTuning.legacyDefault("fluid.ender").seaBanned());
    }
    @Test void operatorsCanDisableRanksOrSeaWithoutRemovingExplicitFluidUseAndNegativeFactorsAreValid() {
        var source = SymbolTuning.legacyDefault("unrecognized");
        var rule = SymbolTuning.overrideFluid(source, JsonParser.parseString("{\"seaBanned\":true,\"grammarRank\":null,\"cardRank\":2,\"accessible\":-2,\"total\":-1}").getAsJsonObject());
        assertTrue(rule.seaBanned()); assertNull(rule.grammarRank()); assertEquals(2, rule.cardRank());
        assertEquals(-2, rule.accessible()); assertEquals(-1, rule.total()); assertFalse(rule.blacklisted());
        assertTrue(SymbolTuning.overrideFluid(source, JsonParser.parseString("{\"blacklisted\":true}").getAsJsonObject()).blacklisted());
    }
    @Test void rejectsInvalidTypesUnknownFieldsNonfiniteOrUnboundedFactorsAndInvalidRanks() {
        var source = SymbolTuning.legacyDefault("unrecognized");
        for (String invalid : new String[]{"{\"seaBanned\":1}", "{\"blacklisted\":\"true\"}", "{\"grammarRank\":-1}",
                "{\"cardRank\":17}", "{\"grammarRank\":1.5}", "{\"accessible\":1000001}", "{\"total\":\"NaN\"}", "{\"guessedOreFactor\":10}"}) {
            assertThrows(IllegalArgumentException.class, () -> SymbolTuning.overrideFluid(source, JsonParser.parseString(invalid).getAsJsonObject()), invalid);
        }
    }
}
