package net.mystcraft.minecraft.client;

import com.google.gson.JsonParser;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** PageBuilder's original160px black-alpha composition and128px bilinear final image. */
final class DynamicPageImages {
    private static final Map<String,int[]> WORDS = words();
    private DynamicPageImages() {}
    static BufferedImage compose(List<String> poem, BufferedImage background, BufferedImage atlas) {
        if(poem.size()!=4 || poem.stream().anyMatch(word->word==null||word.length()>512)
                || background.getWidth()<1||background.getHeight()<1||atlas.getWidth()<8||atlas.getHeight()<8)
            throw new IllegalArgumentException("Invalid dynamic page sources");
        var enlarge=new AffineTransformOp(AffineTransform.getScaleInstance(160D/background.getWidth(),160D/background.getHeight()),AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        BufferedImage image=enlarge.filter(background,null);
        int[][] positions={{48,0},{96,48},{48,96},{0,48}};
        for(int word=0;word<4;word++)for(int component:components(poem.get(word))) {
            int cellW=atlas.getWidth()/8,cellH=atlas.getHeight()/8;
            for(int x=0;x<64;x++)for(int y=0;y<64;y++) {
                int alpha=atlas.getRGB((component%8)*cellW+x*cellW/64,(component/8)*cellH+y*cellH/64)>>>24;
                if(alpha==0)continue;
                int px=positions[word][0]+x,py=positions[word][1]+y,old=image.getRGB(px,py);
                float factor=1-alpha/255F;
                int red=(int)(((old>>>16)&255)/255F*factor*255),green=(int)(((old>>>8)&255)/255F*factor*255),blue=(int)((old&255)/255F*factor*255);
                image.setRGB(px,py,0xff000000|red<<16|green<<8|blue);
            }
        }
        return new AffineTransformOp(AffineTransform.getScaleInstance(.8,.8),AffineTransformOp.TYPE_BILINEAR).filter(image,null);
    }
    static int[] components(String token) {
        String key=token.toLowerCase(Locale.ROOT);int[] known=WORDS.get(key);if(known!=null)return known;
        Random random=new Random(key.hashCode());int count=random.nextInt(10)+3;
        boolean seasonal=key.startsWith("easter");int[] result=new int[seasonal?4:count];
        for(int i=0;i<result.length;i++)result[i]=random.nextInt(seasonal?8:20)+4;
        return result;
    }
    private static Map<String,int[]> words() {
        var result=new HashMap<String,int[]>();
        var stream=DynamicPageImages.class.getResourceAsStream("/assets/mystcraft/classic_symbols.json");
        if(stream==null)throw new IllegalStateException("Missing classic word definitions");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var entries=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("word_components");
            for(var entry:entries.entrySet()) {var array=entry.getValue().getAsJsonArray();int[] values=new int[array.size()];for(int i=0;i<values.length;i++)values[i]=array.get(i).getAsInt();result.put(entry.getKey(),values);}
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read classic words",failure);}
        return Map.copyOf(result);
    }
}
