package ports.buildcraft.builders.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.snapshot.rules.JsonRule;

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
 * <p>Block entity data keeps only what the block entity itself reads back, with loot tables, stored bees and
 * loader-private data removed, and text that could run commands or read data made inert.
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
     * Entity keys never kept: riders are whole other entities nobody pays for, loot tables fill minecarts for
     * free, and loader data is not the entity's own.
     */
    public static final Set<String> DENIED_ENTITY_KEYS = Set.of("Passengers", "LootTable", "LootTableSeed", "Command", "LastOutput",
            "ForgeData", "ForgeCaps", "cardinal_components", "Leash");
    /** Text component keys that run commands or read selectors, scores or saved data with the block's permissions. */
    private static final Set<String> ACTIVE_COMPONENT_KEYS = Set.of("selector", "score", "nbt");

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
     * id is always the right one, with {@link #DENIED_TILE_KEYS} removed and text made inert. Null when the
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
            blockEntity.load(input);
            CompoundTag output = blockEntity.saveWithId();
            DENIED_TILE_KEYS.forEach(output::remove);
            for (String key : Set.copyOf(output.getAllKeys())) {
                if (isTextKey(key) && output.contains(key, Tag.TAG_STRING)) output.putString(key, inertComponentJson(output.getString(key)));
            }
            return output;
        } catch (RuntimeException e) {
            return null;
        }
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
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return json.isEmpty() ? json : "\"\"";
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
