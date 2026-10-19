package ports.jurassicraft.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The original entries contain page-delimited arrays of formatted lines. */
public final class JournalContent {
    private JournalContent() {}

    public static List<List<String>> read(Reader reader) {
        JsonArray entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
        if (entries == null) throw new IllegalArgumentException("Journal must contain entries");
        List<List<String>> result = new ArrayList<>();
        for (JsonElement entry : entries) {
            List<String> lines = new ArrayList<>();
            for (JsonElement line : entry.getAsJsonArray()) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Journal lines must be strings");
                lines.add(line.getAsString());
            }
            result.add(List.copyOf(lines));
        }
        return List.copyOf(result);
    }

    public static Component formatLine(String value) {
        boolean bold = value.startsWith("*");
        if (bold) value = value.substring(1);
        boolean italic = value.startsWith("_");
        if (italic) value = value.substring(1);
        MutableComponent text = Component.literal(value.replace("\\", ""));
        if (bold) text.withStyle(ChatFormatting.BOLD);
        if (italic) text.withStyle(ChatFormatting.ITALIC);
        return text;
    }
}
