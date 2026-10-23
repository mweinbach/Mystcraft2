package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.FluidAmount;

/**
 * Upstream {@code RequiredExtractor} and its five types: what building a block or entity costs, worked out
 * from its state and saved data.
 */
public abstract sealed class RequiredExtractor {
    public List<ItemStack> extractItemsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
        return List.of();
    }

    public List<FluidAmount> extractFluidsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
        return List.of();
    }

    public List<ItemStack> extractItemsFromEntity(CompoundTag entityNbt) {
        return List.of();
    }

    public List<FluidAmount> extractFluidsFromEntity(CompoundTag entityNbt) {
        return List.of();
    }

    /** Items this extractor names directly, for checking rule files. */
    public List<ResourceLocation> literalItems() {
        return List.of();
    }

    /**
     * Not upstream: the top-level keys of the saved data whose contents this extractor costs. Block entity data
     * from a snapshot keeps these and little else (see {@code SchematicSafety#sanitizeTileNbt}).
     */
    public Set<String> costedKeys() {
        return Set.of();
    }

    static Set<String> keysOf(NbtPath path) {
        String first = path.first();
        return first == null ? Set.of() : Set.of(first);
    }

    public static RequiredExtractor fromJson(JsonObject json) {
        String type = json.get("type").getAsString().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "constant" -> new Constant(json);
            case "item_from_block" -> new ItemFromBlock();
            case "item" -> new Item(NbtPath.fromJson(json.get("path")));
            case "items_list" -> new ItemsList(NbtPath.fromJson(json.get("path")));
            case "tank" -> new Tank(NbtPath.fromJson(json.get("path")));
            default -> throw new IllegalArgumentException("Required extractor type not found: " + type);
        };
    }

    /** Fixed items and fluids, which may still read parts from the saved data. */
    public static final class Constant extends RequiredExtractor {
        private final List<ItemStackRef> itemRefs = new ArrayList<>();
        private final List<FluidStackRef> fluidRefs = new ArrayList<>();

        Constant(JsonObject json) {
            if (json.has("items")) for (JsonElement item : json.getAsJsonArray("items")) itemRefs.add(ItemStackRef.fromJson(item.getAsJsonObject()));
            if (json.has("fluids")) for (JsonElement fluid : json.getAsJsonArray("fluids")) fluidRefs.add(FluidStackRef.fromJson(fluid.getAsJsonObject()));
        }

        @Override
        public List<ItemStack> extractItemsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
            return itemRefs.stream().map(ref -> ref.get(tileNbt)).toList();
        }

        @Override
        public List<FluidAmount> extractFluidsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
            return fluidRefs.stream().map(ref -> ref.get(tileNbt)).toList();
        }

        @Override
        public List<ItemStack> extractItemsFromEntity(CompoundTag entityNbt) {
            return itemRefs.stream().map(ref -> ref.get(entityNbt)).toList();
        }

        @Override
        public List<FluidAmount> extractFluidsFromEntity(CompoundTag entityNbt) {
            return fluidRefs.stream().map(ref -> ref.get(entityNbt)).toList();
        }

        @Override
        public List<ResourceLocation> literalItems() {
            return itemRefs.stream().flatMap(ref -> ref.literalItem().stream()).toList();
        }
    }

    /** The block's own item, which is what a block costs when no rule says otherwise. */
    public static final class ItemFromBlock extends RequiredExtractor {
        @Override
        public List<ItemStack> extractItemsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
            return List.of(new ItemStack(blockState.getBlock().asItem()));
        }
    }

    /** One item stored in the saved data, such as an item frame's item. */
    public static final class Item extends RequiredExtractor {
        private final NbtPath path;

        Item(NbtPath path) {
            this.path = path;
        }

        @Override
        public Set<String> costedKeys() {
            return keysOf(path);
        }

        @Override
        public List<ItemStack> extractItemsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
            return read(path.get(tileNbt));
        }

        @Override
        public List<ItemStack> extractItemsFromEntity(CompoundTag entityNbt) {
            return read(path.get(entityNbt));
        }

        private static List<ItemStack> read(Tag tag) {
            return tag instanceof CompoundTag compound ? List.of(ItemStack.of(compound)) : List.of();
        }
    }

    /** A list of stored items, such as a chest's contents. */
    public static final class ItemsList extends RequiredExtractor {
        private final NbtPath path;

        ItemsList(NbtPath path) {
            this.path = path;
        }

        @Override
        public Set<String> costedKeys() {
            return keysOf(path);
        }

        @Override
        public List<ItemStack> extractItemsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
            return read(path.get(tileNbt));
        }

        @Override
        public List<ItemStack> extractItemsFromEntity(CompoundTag entityNbt) {
            return read(path.get(entityNbt));
        }

        private static List<ItemStack> read(Tag tag) {
            if (!(tag instanceof ListTag list)) return List.of();
            List<ItemStack> stacks = new ArrayList<>();
            for (Tag element : list) if (element instanceof CompoundTag compound) stacks.add(ItemStack.of(compound));
            return List.copyOf(stacks);
        }
    }

    /** A stored fluid stack, written as Forge writes fluid stacks: {@code FluidName} and {@code Amount}. */
    public static final class Tank extends RequiredExtractor {
        private final NbtPath path;

        Tank(NbtPath path) {
            this.path = path;
        }

        @Override
        public Set<String> costedKeys() {
            return keysOf(path);
        }

        @Override
        public List<FluidAmount> extractFluidsFromBlock(BlockState blockState, @Nullable CompoundTag tileNbt) {
            return read(path.get(tileNbt));
        }

        @Override
        public List<FluidAmount> extractFluidsFromEntity(CompoundTag entityNbt) {
            return read(path.get(entityNbt));
        }

        private static List<FluidAmount> read(Tag tag) {
            if (!(tag instanceof CompoundTag compound) || tag == NbtJson.NULL || compound.contains("Empty")) return List.of();
            ResourceLocation id = ResourceLocation.tryParse(compound.getString("FluidName"));
            return id == null ? List.of() : Registry.FLUID.getOptional(id)
                    .map(fluid -> List.of(new FluidAmount(fluid, compound.getInt("Amount"))))
                    .orElse(List.of());
        }
    }
}
