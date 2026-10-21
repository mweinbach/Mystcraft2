package mekanism.common.tags;

import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalTags;
import net.minecraft.tags.TagKey;
import ports.mekanism.lib.common.util.Lazy;
import ports.mekanism.lib.registries.IForgeRegistry;
import ports.mekanism.lib.registries.tags.ITag;

public record LazyTagLookup<TYPE>(TagKey<TYPE> key, Lazy<ITag<TYPE>> lazyTag) {

    public static <TYPE> LazyTagLookup<TYPE> create(IForgeRegistry<TYPE> registry, TagKey<TYPE> key) {
        return new LazyTagLookup<>(key, Lazy.of(() -> TagUtils.manager(registry).getTag(key)));
    }

    public static <CHEMICAL extends Chemical<CHEMICAL>> LazyTagLookup<CHEMICAL> create(ChemicalTags<CHEMICAL> registry, TagKey<CHEMICAL> key) {
        return new LazyTagLookup<>(key, Lazy.of(() -> registry.getManager().orElseThrow().getTag(key)));
    }

    public ITag<TYPE> tag() {
        return lazyTag.get();
    }

    public boolean contains(TYPE element) {
        return tag().contains(element);
    }

    public boolean isEmpty() {
        return tag().isEmpty();
    }
}