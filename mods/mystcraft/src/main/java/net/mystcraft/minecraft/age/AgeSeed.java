package net.mystcraft.minecraft.age;

import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Legacy LinkOptions stores Props.Seed as decimal text; modern long tags are also accepted. */
public final class AgeSeed {
    private AgeSeed() {}
    public static OptionalLong requested(CompoundTag book) {
        if (!book.contains("Props")) return OptionalLong.empty();
        if (!book.contains("Props", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Props must be a compound");
        CompoundTag props = book.getCompound("Props");
        if (!props.contains("Seed")) return OptionalLong.empty();
        if (props.contains("Seed", Tag.TAG_LONG)) return OptionalLong.of(props.getLong("Seed"));
        if (props.contains("Seed", Tag.TAG_STRING)) return OptionalLong.of(Long.parseLong(props.getString("Seed")));
        throw new IllegalArgumentException("Seed must be a signed decimal long");
    }
}
