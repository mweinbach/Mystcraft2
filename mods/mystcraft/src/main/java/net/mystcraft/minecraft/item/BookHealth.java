package net.mystcraft.minecraft.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Classic book durability lives in the book itself, including after pickup and linking. */
public final class BookHealth {
    private BookHealth() {}
    public static float maximum(ItemStack book) {
        CompoundTag tag = book.getTag();
        float value = tag != null && tag.contains("MaxHealth", 99) ? tag.getFloat("MaxHealth") : 10;
        return Float.isFinite(value) && value > 0 ? value : 10;
    }
    public static float health(ItemStack book) {
        float maximum = maximum(book);
        float damage = book.hasTag() ? book.getTag().getFloat("damage") : 0;
        return Float.isFinite(damage) ? Math.max(0, Math.min(maximum, maximum - damage)) : maximum;
    }
    public static void set(ItemStack book, float health) {
        if (book.isEmpty()) return;
        float maximum = maximum(book);
        book.getOrCreateTag().putFloat("MaxHealth", maximum);
        book.getOrCreateTag().putFloat("damage", maximum - Math.max(0, Math.min(maximum,
                Float.isFinite(health) ? health : maximum)));
    }
}
