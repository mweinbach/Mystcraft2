package net.mystcraft.minecraft.client;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicPageImagesTest {
    @Test void sourceAlphaBlendAndFourWordPositionsSurviveFinalScale() {
        var background=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<32;x++)for(int y=0;y<32;y++)background.setRGB(x,y,0xffd8c8a0);
        var atlas=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
        // WordData0 uses component1. Four isolated word centers test original source geometry.
        for(int x=64;x<128;x++)for(int y=0;y<64;y++)atlas.setRGB(x,y,0x80ffffff);
        var image=DynamicPageImages.compose(List.of("0","0","0","0"),background,atlas);
        assertEquals(128,image.getWidth());assertEquals(128,image.getHeight());
        for(int[] center:new int[][]{{64,25},{102,64},{64,102},{25,64}})assertEquals(0xff6b634f,image.getRGB(center[0],center[1]));
        assertEquals(0xffd8c8a0,image.getRGB(0,0));
    }
    @Test void arbitraryNativeIdsUseDeterministicClassicWordFallbackAndBoundInput() {
        int[] lower=DynamicPageImages.components("modpack:biome/strange_forest");
        assertArrayEquals(lower,DynamicPageImages.components("MODPACK:BIOME/STRANGE_FOREST"));
        assertTrue(lower.length>=3&&lower.length<=12);
        for(int component:lower)assertTrue(component>=4&&component<24);
        assertFalse(java.util.Arrays.equals(lower,new int[]{0}));
        assertThrows(IllegalArgumentException.class,()->DynamicPageImages.compose(List.of("one"),new BufferedImage(32,32,2),new BufferedImage(512,512,2)));
    }
}
