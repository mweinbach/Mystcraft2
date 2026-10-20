package ports.buildcraft.builders.snapshot.rules;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code RulesLoader}: reads {@code assets/<modid>/compat/buildcraft/builders/index.json} from every
 * loaded mod, then each rule file it lists. Blocks and entities of the mods that ship rules, and of Minecraft,
 * are recorded in blueprints; BuildCraft's own are not, as in upstream release builds.
 */
public final class RulesLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    @Nullable private static Loaded loaded;
    private static final LoadingCache<BlockKey, Set<JsonRule>> BLOCK_RULES_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build(CacheLoader.from(key -> getBlockRulesInternal(key.blockState(), key.tileNbt())));

    private RulesLoader() {}

    private record Loaded(List<JsonRule> rules, Set<String> readDomains) {}

    private record BlockKey(BlockState blockState, @Nullable CompoundTag tileNbt) {}

    private static synchronized Loaded loaded() {
        if (loaded == null) loaded = load();
        return loaded;
    }

    private static Loaded load() {
        List<JsonRule> rules = new ArrayList<>();
        Set<String> readDomains = new HashSet<>();
        for (String domain : Platform.get().modIds()) {
            if (readDomains.contains(domain)) continue;
            String base = "assets/" + domain + "/compat/buildcraft/builders/";
            Optional<Path> index = Platform.get().modResource(domain, base + "index.json");
            if (index.isEmpty()) continue;
            for (JsonElement name : read(index.get()).getAsJsonArray()) {
                String file = base + name.getAsString() + ".json";
                Path path = Platform.get().modResource(domain, file).orElseThrow(() -> new IllegalStateException("Can't read " + file));
                for (JsonElement rule : read(path).getAsJsonArray()) rules.add(JsonRule.fromJson(rule.getAsJsonObject()));
            }
            readDomains.add(domain);
        }
        readDomains.add("minecraft");
        readDomains.removeIf(domain -> domain.startsWith("buildcraft"));
        LOGGER.debug("Loaded {} builder rules; blueprints record blocks from {}", rules.size(), readDomains);
        return new Loaded(List.copyOf(rules), Set.copyOf(readDomains));
    }

    private static JsonElement read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Can't read " + path, e);
        }
    }

    public static List<JsonRule> rules() {
        return loaded().rules();
    }

    /** Upstream {@code READ_DOMAINS}: namespaces whose blocks and entities blueprints record. */
    public static Set<String> readDomains() {
        return loaded().readDomains();
    }

    public static Set<JsonRule> getRules(BlockState blockState, @Nullable CompoundTag tileNbt) {
        return BLOCK_RULES_CACHE.getUnchecked(new BlockKey(blockState, tileNbt));
    }

    public static Set<JsonRule> getRules(ResourceLocation entityId, CompoundTag entityNbt) {
        Set<JsonRule> matching = new HashSet<>();
        String id = entityId.toString();
        for (JsonRule rule : rules()) {
            if (rule.selectors != null && rule.selectors.stream().anyMatch(selector -> selector.matches(id::equals, entityNbt))) matching.add(rule);
        }
        return matching;
    }

    private static Set<JsonRule> getBlockRulesInternal(BlockState blockState, @Nullable CompoundTag tileNbt) {
        CompoundTag nbt = tileNbt == null ? new CompoundTag() : tileNbt;
        Set<JsonRule> matching = new HashSet<>();
        for (JsonRule rule : rules()) {
            if (rule.selectors != null && rule.selectors.stream().anyMatch(selector -> selector.matches(base -> matchesBlock(base, blockState), nbt))) {
                matching.add(rule);
            }
        }
        return matching;
    }

    /** A selector base names a block and optionally {@code [name=value, name=value]} properties, as upstream writes them. */
    static boolean matchesBlock(String base, BlockState blockState) {
        int bracket = base.indexOf('[');
        ResourceLocation id = ResourceLocation.tryParse(bracket < 0 ? base : base.substring(0, bracket));
        Optional<Block> block = id == null ? Optional.empty() : Registry.BLOCK.getOptional(id);
        if (block.isEmpty() || block.get() != blockState.getBlock()) return false;
        if (bracket < 0) return true;
        for (String nameValue : base.substring(bracket + 1, base.indexOf(']')).split(", ")) {
            String[] split = nameValue.split("=");
            if (split.length != 2 || !split[1].equals(propertyValue(blockState, split[0]))) return false;
        }
        return true;
    }

    @Nullable
    private static String propertyValue(BlockState blockState, String name) {
        Property<?> property = blockState.getBlock().getStateDefinition().getProperty(name);
        return property == null ? null : valueName(blockState, property);
    }

    private static <T extends Comparable<T>> String valueName(BlockState blockState, Property<T> property) {
        return property.getName(blockState.getValue(property));
    }

    /** Items named directly by any rule, for checking that rule files only mention items that exist. */
    public static Collection<ResourceLocation> literalItems() {
        Set<ResourceLocation> items = new HashSet<>();
        for (JsonRule rule : rules()) {
            if (rule.requiredExtractors != null) rule.requiredExtractors.forEach(extractor -> items.addAll(extractor.literalItems()));
        }
        return items;
    }
}
