package net.mystcraft.minecraft.guide;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;

/** Localizable manuscript pages; the client reader handles wrapping and overflow. */
public final class GuideContent {
    private static final int PAGE_COUNT = 37;

    private GuideContent() {}

    public static List<Component> pages() {
        return IntStream.rangeClosed(1, PAGE_COUNT)
                .mapToObj(page -> (Component) Component.translatable("book.mystcraft.guide.page." + page))
                .toList();
    }
}
