package com.agricraft.agricraft.common.registry;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.codecs.AgriMutation;
import com.agricraft.agricraft.api.codecs.AgriSoil;
import com.agricraft.agricraft.api.fertilizer.AgriFertilizer;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.api.plant.AgriWeed;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AgriCraft's data-driven registries (plants, weeds, soils, mutations and fertilizers).
 *
 * <p>Upstream AgriCraft 4 on 1.20.1 registers these as synced datapack registries. 1.19.2 has no API for modded
 * datapack registries on Fabric (Fabric API's {@code DynamicRegistries} arrived with 1.19.4), so both loaders load
 * them here with a server reload listener instead, from the same paths a 1.20.1 datapack uses:
 * {@code data/<namespace>/agricraft/<plants|weeds|soils|mutations|fertilizers>/<id>.json}, plus tags in
 * {@code data/<namespace>/tags/agricraft/<type>/<tag>.json}. Each type is held in a frozen vanilla
 * {@link MappedRegistry}, so the upstream {@code Registry} based API keeps working. Unlike a datapack registry,
 * {@code /reload} reloads them.</p>
 *
 * <p>The server's registries are sent to each joining player and again after every reload. The integrated
 * server's own client reads the server's instances directly.</p>
 */
public final class AgriDataRegistries {

	private static final Logger LOGGER = LogUtils.getLogger();
	public static final ResourceLocation SYNC_CHANNEL = new ResourceLocation(AgriApi.MOD_ID, "data_registries");
	public static final ResourceLocation RELOAD_LISTENER_ID = new ResourceLocation(AgriApi.MOD_ID, "data_registries");

	public static final Type<AgriPlant> PLANTS = new Type<>(AgriApi.AGRIPLANTS, AgriPlant.CODEC);
	public static final Type<AgriWeed> WEEDS = new Type<>(AgriApi.AGRIWEEDS, AgriWeed.CODEC);
	public static final Type<AgriSoil> SOILS = new Type<>(AgriApi.AGRISOILS, AgriSoil.CODEC);
	public static final Type<AgriMutation> MUTATIONS = new Type<>(AgriApi.AGRIMUTATIONS, AgriMutation.CODEC);
	public static final Type<AgriFertilizer> FERTILIZERS = new Type<>(AgriApi.AGRIFERTILIZERS, AgriFertilizer.CODEC);
	public static final List<Type<?>> TYPES = List.of(PLANTS, WEEDS, SOILS, MUTATIONS, FERTILIZERS);

	/** The registries the game currently uses; replaced as a whole, never mutated. */
	private static volatile Map<ResourceKey<?>, Registry<?>> current = Map.of();
	/** Whether {@link #current} came from a remote server rather than this JVM's server. */
	private static volatile boolean fromRemote = false;

	private AgriDataRegistries() {
	}

	@SuppressWarnings("unchecked")
	public static <T> Optional<Registry<T>> get(ResourceKey<? extends Registry<T>> key) {
		return Optional.ofNullable((Registry<T>) current.get(key));
	}

	/** True once a server has loaded the registries, or a remote server has sent them. */
	public static boolean isLoaded() {
		return !current.isEmpty();
	}

	/** Drops the registries, when the server stops or the client leaves a remote server. */
	public static void clear(boolean remoteOnly) {
		if (!remoteOnly || fromRemote) {
			current = Map.of();
			fromRemote = false;
		}
	}

	public static PreparableReloadListener reloadListener() {
		return new Listener();
	}

	public record Type<T>(ResourceKey<Registry<T>> key, Codec<T> codec) {

		/** {@code agricraft/plants}: the directory below {@code data/<namespace>/}. */
		public String directory() {
			return this.key.location().getNamespace() + "/" + this.key.location().getPath();
		}

		/** {@code tags/agricraft/plants}. */
		public String tagDirectory() {
			return "tags/" + this.directory();
		}

	}

	/** Raw files for one type, read off-thread. */
	private record Raw(Map<ResourceLocation, JsonElement> elements, Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
	}

	private static final class Listener implements PreparableReloadListener {

		@Override
		public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepProfiler,
		                                      ProfilerFiller applyProfiler, Executor backgroundExecutor, Executor gameExecutor) {
			return CompletableFuture.supplyAsync(() -> {
				Map<Type<?>, Raw> raw = new LinkedHashMap<>();
				for (Type<?> type : TYPES) {
					raw.put(type, new Raw(readJson(manager, type.directory()), new TagLoader<>(id -> Optional.empty(), type.tagDirectory()).load(manager)));
				}
				return raw;
			}, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync(raw -> {
				Map<ResourceKey<?>, Registry<?>> built = new HashMap<>();
				raw.forEach((type, files) -> built.put(type.key(), build(type, files)));
				current = Map.copyOf(built);
				fromRemote = false;
				LOGGER.info("Loaded AgriCraft data: {}", summary());
			}, gameExecutor);
		}

		@Override
		public String getName() {
			return RELOAD_LISTENER_ID.toString();
		}

	}

	private static Map<ResourceLocation, JsonElement> readJson(ResourceManager manager, String directory) {
		Map<ResourceLocation, JsonElement> result = new TreeMap<>();
		int prefix = directory.length() + 1;
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(directory, path -> path.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation file = entry.getKey();
			String path = file.getPath();
			ResourceLocation id = new ResourceLocation(file.getNamespace(), path.substring(prefix, path.length() - ".json".length()));
			try (Reader reader = entry.getValue().openAsReader()) {
				result.put(id, JsonParser.parseReader(reader));
			} catch (Exception e) {
				LOGGER.error("Couldn't read AgriCraft data file {} from {}", id, file, e);
			}
		}
		return result;
	}

	private static <T> Registry<T> build(Type<T> type, Raw files) {
		MappedRegistry<T> registry = new MappedRegistry<>(type.key(), Lifecycle.stable(), null);
		files.elements().forEach((id, json) -> type.codec().parse(JsonOps.INSTANCE, json)
				.resultOrPartial(error -> LOGGER.error("Couldn't parse AgriCraft {} {}: {}", type.key().location().getPath(), id, error))
				.ifPresent(value -> registry.register(ResourceKey.create(type.key(), id), value, Lifecycle.stable())));
		TagLoader<Holder<T>> tagLoader = new TagLoader<>(id -> registry.getHolder(ResourceKey.create(type.key(), id)).map(holder -> holder), type.tagDirectory());
		bindTags(type, registry, tagLoader.build(files.tags()));
		registry.freeze();
		return registry;
	}

	private static <T> void bindTags(Type<T> type, MappedRegistry<T> registry, Map<ResourceLocation, Collection<Holder<T>>> tags) {
		Map<TagKey<T>, List<Holder<T>>> bound = new HashMap<>();
		tags.forEach((id, holders) -> bound.put(TagKey.create(type.key(), id), List.copyOf(holders)));
		registry.bindTags(bound);
	}

	public static String summary() {
		StringBuilder builder = new StringBuilder();
		for (Type<?> type : TYPES) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			Registry<?> registry = current.get(type.key());
			builder.append(registry == null ? 0 : registry.size()).append(' ').append(type.key().location().getPath());
		}
		return builder.toString();
	}

	// Network sync. Values travel as the JSON their codec writes, the format datapacks use.

	public static void write(FriendlyByteBuf buf) {
		Map<ResourceKey<?>, Registry<?>> registries = current;
		buf.writeVarInt(TYPES.size());
		for (Type<?> type : TYPES) {
			writeType(buf, type, registries);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void writeType(FriendlyByteBuf buf, Type<T> type, Map<ResourceKey<?>, Registry<?>> registries) {
		buf.writeResourceLocation(type.key().location());
		Registry<T> registry = (Registry<T>) registries.get(type.key());
		List<Map.Entry<ResourceLocation, String>> entries = new ArrayList<>();
		if (registry != null) {
			for (Map.Entry<ResourceKey<T>, T> entry : registry.entrySet()) {
				type.codec().encodeStart(JsonOps.INSTANCE, entry.getValue())
						.resultOrPartial(error -> LOGGER.error("Couldn't encode AgriCraft {} {}: {}", type.key().location().getPath(), entry.getKey().location(), error))
						.ifPresent(json -> entries.add(Map.entry(entry.getKey().location(), json.toString())));
			}
		}
		buf.writeVarInt(entries.size());
		for (Map.Entry<ResourceLocation, String> entry : entries) {
			buf.writeResourceLocation(entry.getKey());
			buf.writeUtf(entry.getValue(), Integer.MAX_VALUE / 4);
		}
		List<TagKey<T>> tags = registry == null ? List.of() : registry.getTagNames().toList();
		buf.writeVarInt(tags.size());
		for (TagKey<T> tag : tags) {
			buf.writeResourceLocation(tag.location());
			List<ResourceLocation> members = new ArrayList<>();
			registry.getTagOrEmpty(tag).forEach(holder -> holder.unwrapKey().ifPresent(key -> members.add(key.location())));
			buf.writeCollection(members, FriendlyByteBuf::writeResourceLocation);
		}
	}

	/** Reads what {@link #write} wrote. Call {@link #apply} with the result on the client thread. */
	public static Map<ResourceKey<?>, Registry<?>> read(FriendlyByteBuf buf) {
		Map<ResourceKey<?>, Registry<?>> result = new HashMap<>();
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			ResourceLocation key = buf.readResourceLocation();
			Type<?> type = TYPES.stream().filter(t -> t.key().location().equals(key)).findFirst()
					.orElseThrow(() -> new IllegalStateException("Unknown AgriCraft registry " + key));
			result.put(type.key(), readType(buf, type));
		}
		return result;
	}

	private static <T> Registry<T> readType(FriendlyByteBuf buf, Type<T> type) {
		MappedRegistry<T> registry = new MappedRegistry<>(type.key(), Lifecycle.stable(), null);
		int entries = buf.readVarInt();
		for (int i = 0; i < entries; i++) {
			ResourceLocation id = buf.readResourceLocation();
			String json = buf.readUtf(Integer.MAX_VALUE / 4);
			type.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json))
					.resultOrPartial(error -> LOGGER.error("Couldn't decode AgriCraft {} {}: {}", type.key().location().getPath(), id, error))
					.ifPresent(value -> registry.register(ResourceKey.create(type.key(), id), value, Lifecycle.stable()));
		}
		Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
		int tagCount = buf.readVarInt();
		for (int i = 0; i < tagCount; i++) {
			TagKey<T> tag = TagKey.create(type.key(), buf.readResourceLocation());
			List<Holder<T>> members = new ArrayList<>();
			for (ResourceLocation id : buf.readList(FriendlyByteBuf::readResourceLocation)) {
				registry.getHolder(ResourceKey.create(type.key(), id)).ifPresent(members::add);
			}
			tags.put(tag, members);
		}
		registry.bindTags(tags);
		registry.freeze();
		return registry;
	}

	/** Installs registries a remote server sent. */
	public static void apply(Map<ResourceKey<?>, Registry<?>> registries) {
		current = Map.copyOf(registries);
		fromRemote = true;
		LOGGER.info("Received AgriCraft data: {}", summary());
	}

}
