package net.mystcraft.minecraft.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassicPageScrollTest {
    @Test void wheelKeepsPartiallyVisiblePageHitTestsAligned() {
        var scroll=new ClassicPageScroll();scroll.layout(40,166);scroll.wheel(-1);
        assertEquals(20,scroll.pixels());
        // Row zero remains visible for20pixels; the next row begins at viewportY21.
        assertEquals(0,scroll.cell(58.5,20.5,58,20,166));
        assertEquals(-1,scroll.cell(58.5,40.5,58,20,166));
        assertEquals(5,scroll.cell(58.5,41.5,58,20,166));
        assertEquals(-1,scroll.cell(88.5,41.5,58,20,166));
        assertEquals(-1,scroll.cell(58,19.9,58,20,166));
        assertEquals(-1,scroll.cell(58,186,58,20,166));
    }
    @Test void trackDragMatchesPinnedSliderAndClampsAfterSearch() {
        var scroll=new ClassicPageScroll();scroll.layout(145,166);
        assertEquals(1028,scroll.maximum());
        scroll.begin(91.5,0,166); // source (mouseY-11.5)/(height-23)
        assertEquals(575,scroll.pixels());
        scroll.drag(10000,0,166);assertEquals(1028,scroll.pixels());
        scroll.drag(-100,0,166);assertEquals(0,scroll.pixels());
        scroll.end();scroll.drag(10000,0,166);assertEquals(0,scroll.pixels());
        scroll.wheel(-1);assertEquals(20,scroll.pixels());
        scroll.layout(5,166);assertEquals(0,scroll.pixels());assertEquals(0,scroll.maximum());
    }
    @Test void portableTrailingRowCanBeFullyRevealedWithoutWholeRowJump() {
        var scroll=new ClassicPageScroll();scroll.layout(15,113);
        assertEquals(15,scroll.maximum());scroll.wheel(-1);assertEquals(15,scroll.pixels());
        assertEquals(10,scroll.cell(1,100,0,19,113));
        scroll.wheel(1);assertEquals(0,scroll.pixels());
    }
}
