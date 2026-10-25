package com.agricraft.agricraft.api.codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.agricraft.agricraft.common.util.Platform;

import java.util.List;
import java.util.Optional;

public record AgriProduct(ExtraCodecs.TagOrElementLocation item, CompoundTag nbt, int min, int max, double chance,
                          boolean required) {

	public static final Codec<AgriProduct> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("item").forGetter(product -> product.item),
			CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(product -> product.nbt.isEmpty() ? Optional.empty() : Optional.of(product.nbt)),
			Codec.INT.fieldOf("min").forGetter(product -> product.min),
			Codec.INT.fieldOf("max").forGetter(product -> product.max),
			Codec.DOUBLE.fieldOf("chance").forGetter(product -> product.chance),
			Codec.BOOL.fieldOf("required").forGetter(product -> product.required)
	).apply(instance, AgriProduct::new));

	public AgriProduct(ExtraCodecs.TagOrElementLocation item, Optional<CompoundTag> nbt, int min, int max, double chance, boolean required) {
		// codec use
		this(item, nbt.orElse(new CompoundTag()), min, max, chance, required);
	}

	public boolean shouldDrop(RandomSource random) {
		return this.chance > random.nextDouble();
	}

	public static Builder builder() {
		return new Builder();
	}

	public int getAmount(RandomSource random) {
		// nextIntBetweenInclusive throws when max < min, which a datapack can declare
		return this.max <= this.min ? this.min : random.nextIntBetweenInclusive(this.min, this.max);
	}

	/**
	 * The items this product can be right now: the item, or the members of the tag. Unknown items (air) are left out,
	 * so a tag no loaded mod fills (e.g. {@code #agricraft:nuggets/tin} without a tin mod) resolves to an empty list.
	 */
	public List<Item> resolve() {
		return Platform.get().getItemsFromLocation(this.item).stream().filter(item -> item != null && item != Items.AIR).toList();
	}

	/** Whether this product resolves to at least one item. */
	public boolean resolves() {
		return !this.resolve().isEmpty();
	}

	/**
	 * Picks one of the product's items and its amount. Empty when the product resolves to no item: 4.0.6 called
	 * {@code nextInt(0)} there and crashed the server on harvest, clipping, raking and block drops.
	 */
	public Optional<ItemStack> roll(RandomSource random) {
		List<Item> possible = this.resolve();
		if (possible.isEmpty()) {
			return Optional.empty();
		}
		Item item = possible.get(random.nextInt(possible.size()));
		ItemStack stack = new ItemStack(item, this.getAmount(random));
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		if (!this.nbt.isEmpty()) {
			stack.getOrCreateTag().merge(this.nbt);
		}
		return Optional.of(stack);
	}

	public static class Builder {
		ExtraCodecs.TagOrElementLocation item = new ExtraCodecs.TagOrElementLocation(new ResourceLocation("minecraft", "air"), false);
		CompoundTag nbt = new CompoundTag();
		int min = 1;
		int max = 3;
		double chance = 0.95;
		boolean required = true;

		public AgriProduct build() {
			return new AgriProduct(item, nbt, min, max, chance, required);
		}

		public Builder item(String location) {
			this.item = new ExtraCodecs.TagOrElementLocation(new ResourceLocation(location), false);
			return this;
		}
		public Builder item(String namespace, String path) {
			this.item = new ExtraCodecs.TagOrElementLocation(new ResourceLocation(namespace, path), false);
			return this;
		}
		public Builder tag(String location) {
			this.item = new ExtraCodecs.TagOrElementLocation(new ResourceLocation(location), true);
			return this;
		}
		public Builder tag(String namespace, String path) {
			this.item = new ExtraCodecs.TagOrElementLocation(new ResourceLocation(namespace, path), true);
			return this;
		}
		public Builder nbt(CompoundTag nbt) {
			this.nbt = nbt;
			return this;
		}
		public Builder count(int min, int max, double chance) {
			this.min = min;
			this.max = max;
			this.chance = chance;
			return this;
		}
		public Builder required(boolean required) {
			this.required = required;
			return this;
		}

	}
}
