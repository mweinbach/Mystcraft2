package net.mystcraft.minecraft.linking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkDestinationTest {

    private LinkDestination example() {
        return new LinkDestination(new ResourceLocation("minecraft", "the_nether"), -123.125, 74.5, 912.75, 135, -30);
    }

    @Test
    void roundTripsExactDestinationAndOrientation() {
        assertEquals(example(), LinkDestination.readTag(example().toTag()).orElseThrow());
    }

    @Test
    void rejectsMissingFieldsInsteadOfDefaultingToWorldOrigin() {
        for (String field : new String[]{"Dimension", "X", "Y", "Z", "Yaw", "Pitch"}) {
            CompoundTag tag = example().toTag();
            tag.remove(field);
            assertTrue(LinkDestination.readTag(tag).isEmpty(), field);
        }
    }

    @Test
    void rejectsMalformedDimensionsAndUnknownVersions() {
        CompoundTag tag = example().toTag();
        tag.putString("Dimension", "Not a dimension!");
        assertTrue(LinkDestination.readTag(tag).isEmpty());
        tag = example().toTag();
        tag.putInt("Version", 2);
        assertTrue(LinkDestination.readTag(tag).isEmpty());
    }

    @Test
    void rejectsNonfiniteAndOutOfRangeCoordinates() {
        for (double coordinate : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 30000000}) {
            CompoundTag tag = example().toTag();
            tag.putDouble("X", coordinate);
            assertTrue(LinkDestination.readTag(tag).isEmpty());
        }
        CompoundTag tag = example().toTag();
        tag.putFloat("Pitch", Float.NaN);
        assertTrue(LinkDestination.readTag(tag).isEmpty());
        tag.putFloat("Pitch", 91);
        assertTrue(LinkDestination.readTag(tag).isEmpty());
    }
}
