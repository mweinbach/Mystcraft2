package net.mystcraft.minecraft.age;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeSeedTest {
    @Test void acceptsExactSignedLimitsZeroAndLegacyStringWithoutRewriting() {
        for (long seed : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            CompoundTag book = new CompoundTag(), props = new CompoundTag();
            props.putString("Seed", Long.toString(seed)); book.put("Props", props);
            assertEquals(seed, AgeSeed.requested(book).orElseThrow());
            assertEquals(Long.toString(seed), props.getString("Seed"));
            props.putLong("Seed", seed);
            assertEquals(seed, AgeSeed.requested(book).orElseThrow());
        }
        assertTrue(AgeSeed.requested(new CompoundTag()).isEmpty());
    }
    @Test void rejectsOverflowMalformedValuesAndWrongTypesInsteadOfBecomingSeedZero() {
        for (String invalid : new String[]{"", "not a seed", "9223372036854775808", "-9223372036854775809", " 1"}) {
            CompoundTag book = new CompoundTag(), props = new CompoundTag();
            props.putString("Seed", invalid); book.put("Props", props);
            assertThrows(IllegalArgumentException.class, () -> AgeSeed.requested(book));
        }
        CompoundTag book = new CompoundTag(), props = new CompoundTag(); props.putInt("Seed", 0); book.put("Props", props);
        assertThrows(IllegalArgumentException.class, () -> AgeSeed.requested(book));
        book.putString("Props", "invalid");
        assertThrows(IllegalArgumentException.class, () -> AgeSeed.requested(book));
    }
}
