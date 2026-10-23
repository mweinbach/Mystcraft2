package ports.buildcraft.builders.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.snapshot.rules.JsonRule;
import ports.buildcraft.builders.snapshot.rules.RulesLoader;

/**
 * Not upstream. Survival players can craft the electronic library and the builder, and a client can upload
 * any snapshot it likes, so nothing in a snapshot read from saved data is trusted: every block and entity is
 * rebuilt from the rules (see {@link SchematicBlock#canonicalize}) and the parts no recording could contain are
 * refused or removed here.
 *
 * <p>Blocks a builder must never place, from scanning or from forged data: anything in
 * {@code buildcraft:schematic_blacklist} or the built-in list below (operator and technical blocks, and blocks
 * survival players cannot obtain), operator-only blocks ({@link GameMasterBlock}), unbreakable blocks, and
 * blocks that would cost nothing because they have no item and no rule says what they cost.
 *
 * <p>Block entity data keeps only what the block entity itself reads back, and of that only what a rule costs or
 * what merely names the block or changes its look ({@link #KEPT_TILE_KEYS}); loot tables, stored bees, operating
 * state and loader-private data are removed, and text that could run commands or read data is made inert. Entity data read
 * while a level is {@linkplain #readInLevel given} is rebuilt the same way, through a new entity of its type.
 * Text components longer than {@value #MAX_COMPONENT_LENGTH} characters or with more than
 * {@value #MAX_COMPONENT_OPENINGS} arrays and objects are emptied before anything parses them, and a block
 * entity or entity whose data is still over {@value #MAX_SCHEMATIC_NBT_BYTES} bytes is refused.
 */
public final class SchematicSafety {
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registry.BLOCK_REGISTRY, BuildCraft.id("schematic_blacklist"));
    /** The tag's vanilla contents, also checked here so that the list holds before tags are bound. */
    public static final List<Block> BUILT_IN_BLACKLIST = List.of(
            Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.JIGSAW, Blocks.BARRIER, Blocks.LIGHT,
            Blocks.BEDROCK, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL,
            Blocks.SPAWNER, Blocks.REINFORCED_DEEPSLATE, Blocks.BUDDING_AMETHYST, Blocks.MOVING_PISTON,
            Blocks.PETRIFIED_OAK_SLAB, Blocks.FROSTED_ICE, Blocks.BUBBLE_COLUMN);
    /**
     * Block entity keys never kept: loot tables fill containers for free, stored bees are free mobs, command
     * and spawner data belong to blocks that are refused anyway, and loader data is not the block's own.
     */
    public static final Set<String> DENIED_TILE_KEYS = Set.of("LootTable", "LootTableSeed", "Bees", "Command", "LastOutput",
            "SpawnData", "SpawnPotentials", "ExitPortal", "ForgeData", "ForgeCaps", "cardinal_components");
    /**
     * Block entity keys kept besides those a matching rule {@linkplain ports.buildcraft.builders.snapshot.rules.RequiredExtractor#costedKeys
     * costs} (such as a container's {@code Items}): the id and position, and what only names the block or changes
     * how it looks: custom names, container locks, sign text and colour, banner patterns, skull owners and a
     * lectern's page. Everything else is operating state that would come free with the block, such as a
     * furnace's burn time and stored experience ({@code RecipesUsed}), a brewing stand's fuel or a beacon's
     * paid-for effects, and is dropped.
     */
    public static final Set<String> KEPT_TILE_KEYS = Set.of("id", "x", "y", "z", "CustomName", "Lock",
            "Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4",
            "Color", "GlowingText", "Patterns", "SkullOwner", "Page");
    /**
     * Entity keys never kept: riders are whole other entities nobody pays for, loot tables fill minecarts for
     * free, and loader data is not the entity's own.
     */
    public static final Set<String> DENIED_ENTITY_KEYS = Set.of("Passengers", "LootTable", "LootTableSeed", "Command", "LastOutput",
            "ForgeData", "ForgeCaps", "cardinal_components", "Leash");
    /** Text component keys that run commands or read selectors, scores or saved data with the block's permissions. */
    private static final Set<String> ACTIVE_COMPONENT_KEYS = Set.of("selector", "score", "nbt");
    /** The longest text component kept, as JSON: vanilla's limit for a book page or a command argument. */
    public static final int MAX_COMPONENT_LENGTH = 32767;
    /**
     * The most {@code [} and {@code {} a text component may hold, quoted or not. Counting every one bounds how
     * deeply Gson, {@code Component.Serializer} and {@link #inert} recurse, whatever the JSON's quoting or comments.
     */
    public static final int MAX_COMPONENT_OPENINGS = 256;
    /** The most data one block entity or entity in a blueprint may carry, as written NBT. */
    public static final int MAX_SCHEMATIC_NBT_BYTES = 1024 * 1024;
    private static final String EMPTY_COMPONENT = "\"\"";
    private static final ThreadLocal<Level> READ_LEVEL = new ThreadLocal<>();

    /** Something that reads a snapshot. */
    @FunctionalInterface
    public interface SnapshotReader<T> {
        T read() throws IOException, InvalidSnapshotException;
    }

    private SchematicSafety() {}

    /** A block no builder may place, whatever a schematic says. */
    public static boolean isForbidden(BlockState state) {
        Block block = state.getBlock();
        return state.is(BLACKLIST) || BUILT_IN_BLACKLIST.contains(block) || block instanceof GameMasterBlock
                || state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0;
    }

    /** Whether the block has a cost: a matching rule says what it costs, or it has an item to cost. */
    public static boolean hasCost(BlockState state, Set<JsonRule> rules) {
        return rules.stream().anyMatch(rule -> rule.requiredExtractors != null) || state.getBlock().asItem() != Items.AIR;
    }

    /**
     * The block entity data a recording of {@code state} could hold: the given data read into a new block
     * entity of the state's own type and written back, so only what that block entity reads survives and its
     * id is always the right one; then only {@link #KEPT_TILE_KEYS} and the keys a matching rule costs are
     * kept, never {@link #DENIED_TILE_KEYS}, and text is made inert. Null when the
     * state has no block entity or the data cannot be read. Data this returns comes back unchanged.
     */
    @Nullable
    public static CompoundTag sanitizeTileNbt(BlockState state, @Nullable CompoundTag nbt) {
        if (nbt == null || !state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
            if (blockEntity == null) return null;
            CompoundTag input = nbt.copy();
            DENIED_TILE_KEYS.forEach(input::remove);
            boundTextKeys(input);
            blockEntity.load(input);
            CompoundTag output = blockEntity.saveWithId();
            Set<String> kept = new HashSet<>(KEPT_TILE_KEYS);
            for (JsonRule rule : RulesLoader.getRules(state, output)) {
                if (rule.requiredExtractors != null) rule.requiredExtractors.forEach(extractor -> kept.addAll(extractor.costedKeys()));
            }
            kept.removeAll(DENIED_TILE_KEYS);
            for (String key : Set.copyOf(output.getAllKeys())) {
                if (!kept.contains(key)) output.remove(key);
            }
            for (String key : Set.copyOf(output.getAllKeys())) {
                if (isTextKey(key) && output.contains(key, Tag.TAG_STRING)) output.putString(key, inertComponentJson(output.getString(key)));
            }
            return output;
        } catch (RuntimeException | StackOverflowError e) {
            // A block entity that recurses on data it is given is refused like one that throws.
            return null;
        }
    }

    /** Empties text keys too long or nested to parse safely, before the block entity or entity parses them. */
    private static void boundTextKeys(CompoundTag tag) {
        for (String key : Set.copyOf(tag.getAllKeys())) {
            if (isTextKey(key) && tag.contains(key, Tag.TAG_STRING) && !isBoundedComponent(tag.getString(key))) tag.putString(key, EMPTY_COMPONENT);
        }
    }

    /** Whether text component JSON is short and shallow enough to parse: see {@link #MAX_COMPONENT_OPENINGS}. */
    public static boolean isBoundedComponent(String json) {
        if (json.length() > MAX_COMPONENT_LENGTH) return false;
        int openings = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if ((c == '[' || c == '{') && ++openings > MAX_COMPONENT_OPENINGS) return false;
        }
        return true;
    }

    /**
     * Runs {@code reader} with {@code level} available to {@link SchematicEntity#canonicalize}, which then rebuilds
     * each entity's data through a new entity of its type. The electronic library reads uploads this way.
     */
    public static <T> T readInLevel(Level level, SnapshotReader<T> reader) throws IOException, InvalidSnapshotException {
        Level previous = READ_LEVEL.get();
        READ_LEVEL.set(level);
        try {
            return reader.read();
        } finally {
            if (previous == null) READ_LEVEL.remove();
            else READ_LEVEL.set(previous);
        }
    }

    /** The level given to {@link #readInLevel}, if a read is running in one on this thread. */
    @Nullable
    public static Level readLevel() {
        return READ_LEVEL.get();
    }

    /** Refuses {@code nbt} if it is over {@link #MAX_SCHEMATIC_NBT_BYTES} when written. */
    public static void requireBounded(@Nullable CompoundTag nbt, String what) throws InvalidSnapshotException {
        if (nbt != null && nbtBytes(nbt, MAX_SCHEMATIC_NBT_BYTES) > MAX_SCHEMATIC_NBT_BYTES) {
            throw new InvalidSnapshotException("The " + what + " is over " + MAX_SCHEMATIC_NBT_BYTES + " bytes");
        }
    }

    /** How many bytes {@code nbt} takes written, counting no further than just past {@code limit}. */
    static long nbtBytes(CompoundTag nbt, long limit) {
        final class Counter extends OutputStream {
            long count;

            @Override
            public void write(int b) throws IOException {
                if (++count > limit) throw new IOException("over the limit");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                count += len;
                if (count > limit) throw new IOException("over the limit");
            }
        }
        Counter counter = new Counter();
        try {
            NbtIo.write(nbt, new DataOutputStream(counter));
        } catch (IOException e) {
            return Math.max(counter.count, limit + 1);
        }
        return counter.count;
    }

    /** Sign lines (filtered or not) and custom names are text components. */
    private static boolean isTextKey(String key) {
        return key.startsWith("Text") || key.startsWith("FilteredText") || key.equals("CustomName");
    }

    /**
     * The entity data a recording could hold: {@link #DENIED_ENTITY_KEYS} removed and the custom name made
     * inert. The id must name an entity type a capturing rule accepts; that is checked by the caller.
     */
    public static CompoundTag sanitizeEntityNbt(CompoundTag nbt) {
        CompoundTag output = nbt.copy();
        DENIED_ENTITY_KEYS.forEach(output::remove);
        boundTextKeys(output);
        if (output.contains("CustomName", Tag.TAG_STRING)) output.putString("CustomName", inertComponentJson(output.getString("CustomName")));
        return output;
    }

    @Nullable
    public static ResourceLocation entityId(CompoundTag nbt) {
        return nbt.contains("id", Tag.TAG_STRING) ? ResourceLocation.tryParse(nbt.getString("id")) : null;
    }

    /**
     * A text component without click events, and with selector, score and saved data parts emptied: signs
     * resolve those with operator permissions. Unchanged text keeps its exact JSON.
     */
    static String inertComponentJson(String json) {
        if (!isBoundedComponent(json)) return EMPTY_COMPONENT;
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return json.isEmpty() ? json : EMPTY_COMPONENT;
        }
        JsonElement inert = inert(parsed);
        return inert.equals(parsed) ? json : inert.toString();
    }

    private static JsonElement inert(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(child -> array.add(inert(child)));
            return array;
        }
        if (!element.isJsonObject()) return element;
        JsonObject object = element.getAsJsonObject();
        if (ACTIVE_COMPONENT_KEYS.stream().anyMatch(object::has)) {
            JsonObject empty = new JsonObject();
            empty.addProperty("text", "");
            return empty;
        }
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals("clickEvent")) continue;
            copy.add(entry.getKey(), inert(entry.getValue()));
        }
        return copy;
    }
}
