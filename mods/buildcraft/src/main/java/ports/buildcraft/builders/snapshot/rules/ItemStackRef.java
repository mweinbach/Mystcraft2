package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonObject;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Upstream {@code ItemStackRef}: an item, count and tag, each given directly or read from the block entity or
 * entity data. Items have no metadata after the flattening, so upstream's {@code meta} has no equivalent.
 */
public final class ItemStackRef {
    private final NbtRef item;
    @Nullable private final NbtRef amount;
    @Nullable private final NbtRef tagCompound;

    private ItemStackRef(NbtRef item, @Nullable NbtRef amount, @Nullable NbtRef tagCompound) {
        this.item = item;
        this.amount = amount;
        this.tagCompound = tagCompound;
    }

    public static ItemStackRef fromJson(JsonObject json) {
        NbtRef item = NbtRef.fromJson(json.get("item"));
        if (item == null) throw new IllegalArgumentException("An item reference needs an item: " + json);
        return new ItemStackRef(item, NbtRef.fromJson(json.get("amount")), NbtRef.fromJson(json.get("tagCompound")));
    }

    /** The item this reference names directly, for checking rule files; empty when it is read from NBT. */
    public Optional<ResourceLocation> literalItem() {
        return item.isPath() ? Optional.empty() : item.get(null).map(Tag::getAsString).map(ResourceLocation::tryParse);
    }

    /** Empty when the item is unknown, so a rule naming a missing item never asks for something unobtainable. */
    public ItemStack get(@Nullable Tag nbt) {
        Optional<Item> found = item.get(nbt).map(Tag::getAsString).map(ResourceLocation::tryParse).flatMap(Registry.ITEM::getOptional);
        if (found.isEmpty()) return ItemStack.EMPTY;
        int count = Optional.ofNullable(amount).flatMap(ref -> ref.get(nbt))
                .filter(NumericTag.class::isInstance).map(tag -> ((NumericTag) tag).getAsInt()).orElse(1);
        ItemStack stack = new ItemStack(found.get(), count);
        Optional.ofNullable(tagCompound).flatMap(ref -> ref.get(nbt))
                .filter(CompoundTag.class::isInstance).map(tag -> ((CompoundTag) tag).copy()).ifPresent(stack::setTag);
        return stack;
    }
}
