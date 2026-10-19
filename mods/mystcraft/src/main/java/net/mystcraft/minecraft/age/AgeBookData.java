package net.mystcraft.minecraft.age;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.minecraft.item.DescriptiveBookItem;
import net.mystcraft.minecraft.writing.PageData;

/** A descriptive book is allocated only when its panel is activated, as in classic Mystcraft. */
public final class AgeBookData {
    public static final int MAX_PAGES = 256;
    public static final int MAX_TITLE = 21;
    public static final String AGE = "MystcraftAge";
    private AgeBookData() {}

    public static ItemStack create(String title, List<CompoundTag> pages) {
        if (!validPages(pages)) return ItemStack.EMPTY;
        ItemStack book = new ItemStack(AgeRegistry.DESCRIPTIVE_BOOK.get());
        PageData.writePages(book.getOrCreateTag(), pages);
        String name = title == null ? "" : title.strip();
        if (name.length() > MAX_TITLE) name = name.substring(0, MAX_TITLE);
        if (!name.isEmpty()) book.setHoverName(Component.literal(name));
        return book;
    }
    public static boolean isDescriptive(ItemStack book) { return book.getItem() instanceof DescriptiveBookItem; }
    public static boolean validPages(List<CompoundTag> pages) {
        if (pages.isEmpty() || pages.size() > MAX_PAGES || !PageData.isLinkPanel(pages.get(0))) return false;
        for (int i = 1; i < pages.size(); i++) if (PageData.isLinkPanel(pages.get(i))) return false;
        return true;
    }
    public static boolean isUnallocated(ItemStack book) {
        return isDescriptive(book) && book.hasTag() && !book.getTag().contains(AGE)
                && validPages(PageData.readPages(book.getTag()));
    }
    public static Optional<UUID> ageId(ItemStack book) {
        if (!isDescriptive(book) || !book.hasTag()) return Optional.empty();
        CompoundTag age = book.getTag().getCompound(AGE);
        return age.hasUUID("Id") ? Optional.of(age.getUUID("Id")) : Optional.empty();
    }
    public static void assign(ItemStack book, UUID id, long number, long seed) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putUUID("Id", id); tag.putLong("Number", number); tag.putLong("Seed", seed);
        book.getOrCreateTag().put(AGE, tag);
    }
    public static List<CompoundTag> pages(ItemStack book) {
        return book.hasTag() ? PageData.readPages(book.getTag()) : List.of();
    }
}
