package net.mystcraft.minecraft.instability;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstabilityDecksTest {
    @Test void seededDecksPersistAndInvalidCardsAreReconciled() {
        CompoundTag first=InstabilityDecks.reconcile(919,new CompoundTag());
        assertEquals(first,InstabilityDecks.reconcile(919,new CompoundTag()));
        assertNotEquals(first,InstabilityDecks.reconcile(920,new CompoundTag()));
        assertEquals(first,InstabilityDecks.reconcile(920,first));
        first.getList("basic",8).add(net.minecraft.nbt.StringTag.valueOf("unknown"));
        CompoundTag repaired=InstabilityDecks.reconcile(919,first);
        assertFalse(repaired.getList("basic",8).stream().anyMatch(value->value.getAsString().equals("unknown")));
    }
    @Test void noScoreWithoutLegitimateProfileAndDifficultyUsesClassicTruncation() {
        assertEquals(0,InstabilityDecks.score(false,100000,20000,30000,0,0,3));
        assertEquals(25,InstabilityDecks.score(true,101,0,0,0,0,0));
        assertEquals(50,InstabilityDecks.score(true,101,0,0,0,0,1));
        assertEquals(176,InstabilityDecks.score(true,101,0,0,0,0,3));
    }
    @Test void eachDeckSpendsOwnThresholdBudgetAndStopsAtFirstUnaffordableCard() {
        CompoundTag deck=new CompoundTag(); net.minecraft.nbt.ListTag cards=new net.minecraft.nbt.ListTag();
        cards.add(net.minecraft.nbt.StringTag.valueOf("enemyregen,g"));
        cards.add(net.minecraft.nbt.StringTag.valueOf("poison")); deck.put("basic",cards);
        assertTrue(InstabilityDecks.levels(deck,999).isEmpty());
        assertEquals(java.util.Map.of("enemyregen,g",1),InstabilityDecks.levels(deck,1499));
        assertEquals(java.util.Map.of("enemyregen,g",1,"poison",1),InstabilityDecks.levels(deck,1500));
        assertEquals(10000,InstabilityDecks.THRESHOLDS.get("destructive"));
        assertFalse(InstabilityDecks.COSTS.containsKey("decayblack"),"Pinned source does not put black decay into automatic decks");
    }
}
