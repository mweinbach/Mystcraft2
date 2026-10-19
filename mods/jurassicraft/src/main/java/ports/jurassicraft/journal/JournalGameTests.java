package ports.jurassicraft.journal;

import java.util.HashSet;
import java.io.StringReader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalGameTests {
    private JournalGameTests() {}
    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void variantsPreserveAuthor(GameTestHelper h) {
        var ids = new HashSet<String>();
        for (JournalType author : JournalType.values()) {
            ItemStack stack = JournalItem.create(author);
            ItemStack restored = ItemStack.of(stack.save(new CompoundTag()));
            if (!ids.add(author.id()) || JournalItem.type(restored) != author || !restored.is(JournalRegistry.JOURNAL.get()))
                h.fail("each journal author must have a distinct, persistent variant");
        }
        ItemStack invalid = new ItemStack(JournalRegistry.JOURNAL.get());
        invalid.getOrCreateTag().putString("JournalAuthor", "unknown");
        if (JournalItem.type(invalid) != JournalType.CHEF_ALEJANDRO) h.fail("invalid author must use the original default");
        h.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void entriesPreservePageBreaksAndFormatting(GameTestHelper h) {
        var entries = JournalContent.read(new StringReader("{\"entries\":[[\"*_One\",\"Two\"],[],[\"Three\"]]}"));
        if (entries.size() != 3 || !entries.get(1).isEmpty() || !entries.get(2).equals(java.util.List.of("Three")))
            h.fail("journal entry boundaries, including blank pages, must survive parsing");
        var formatted = JournalContent.formatLine(entries.get(0).get(0));
        if (!formatted.getString().equals("One") || !formatted.getStyle().isBold() || !formatted.getStyle().isItalic())
            h.fail("journal line markers must retain bold and italic formatting");
        if (!JournalContent.formatLine("a\\b").getString().equals("ab")) h.fail("original journal escape characters must be removed");
        try {
            JournalContent.read(new StringReader("{\"entries\":[[42]]}"));
            h.fail("malformed journal line must fail cleanly");
        } catch (IllegalArgumentException expected) { }
        h.succeed();
    }
}
