package net.mystcraft.minecraft.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Classic GuiUtils four-word geometry using the original component atlas and word definitions. */
final class ClassicBookGlyphs {
    private static final ResourceLocation ATLAS = new ResourceLocation("mystcraft", "textures/symbolcomponents.png");
    private static final Map<String, int[]> WORDS = loadWords();
    private ClassicBookGlyphs() {}

    static void draw(PoseStack pose, SymbolRegistry.Definition symbol, float x, float y, float size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, ATLAS);
        RenderSystem.setShaderColor(0, 0, 0, 1);
        if (symbol == null) {
            word(pose, new int[] {0}, x, y, size);
        } else {
            float s = size / 2 / 2.414213562373095F;
            float o = s * 1.414213562373095F;
            float[][] positions = {{o, 0}, {o * 2, o}, {o, o * 2}, {0, o}};
            List<String> poem = symbol.poem();
            for (int i = 0; i < Math.min(4, poem.size()); i++) {
                word(pose, components(poem.get(i)), x + positions[i][0], y + positions[i][1], 2 * s);
            }
            if (poem.isEmpty()) word(pose, new int[] {0}, x + o, y + o, 2 * s);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    static void drawWord(PoseStack pose, String token, float x, float y, float size) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderTexture(0, ATLAS); RenderSystem.setShaderColor(0,0,0,1);
        word(pose,components(token),x,y,size);
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.disableBlend();
    }

    private static void word(PoseStack pose, int[] components, float x, float y, float size) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 64, size / 64, 1);
        for (int component : components) {
            GuiComponent.blit(pose, 0, 0, (component % 8) * 64F, (component / 8) * 64F, 64, 64, 512, 512);
        }
        pose.popPose();
    }

    private static int[] components(String token) {
        String key = token.toLowerCase(Locale.ROOT);
        int[] defined = WORDS.get(key);
        if (defined != null) return defined;
        // DrawableWordManager's deterministic unknown-word fallback, including its Easter word range.
        Random random = new Random(key.hashCode());
        int count = random.nextInt(10) + 3;
        boolean seasonal = key.startsWith("easter");
        int[] values = new int[seasonal ? 4 : count];
        for (int i = 0; i < values.length; i++) values[i] = random.nextInt(seasonal ? 8 : 20) + 4;
        return values;
    }

    private static Map<String, int[]> loadWords() {
        Map<String, int[]> words = new HashMap<>();
        var stream = ClassicBookGlyphs.class.getResourceAsStream("/assets/mystcraft/classic_symbols.json");
        if (stream == null) throw new IllegalStateException("Missing classic symbol word definitions");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("word_components");
            for (var entry : entries.entrySet()) {
                var list = entry.getValue().getAsJsonArray();
                int[] values = new int[list.size()];
                for (int i = 0; i < values.length; i++) values[i] = list.get(i).getAsInt();
                words.put(entry.getKey().toLowerCase(Locale.ROOT), values);
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load classic glyph definitions", error); }
        return Map.copyOf(words);
    }
}
