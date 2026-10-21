/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceKey;
import org.objectweb.asm.Type;
import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.eventbus.api.Event;
import ports.mekanism.lib.fluids.FluidTypes;
import ports.mekanism.lib.fml.ModContainer;
import ports.mekanism.lib.fml.ModList;
import ports.mekanism.lib.fml.ModLoadingContext;
import ports.mekanism.lib.fml.common.Mod;
import ports.mekanism.lib.fml.config.ConfigTracker;
import ports.mekanism.lib.fml.config.ModConfig;
import ports.mekanism.lib.fml.event.lifecycle.FMLClientSetupEvent;
import ports.mekanism.lib.fml.event.lifecycle.FMLCommonSetupEvent;
import ports.mekanism.lib.fml.event.lifecycle.InterModEnqueueEvent;
import ports.mekanism.lib.fml.event.lifecycle.InterModProcessEvent;
import ports.mekanism.lib.fml.loading.FMLPaths;
import ports.mekanism.lib.fml.loading.moddiscovery.ModAnnotation;
import ports.mekanism.lib.forgespi.language.ModFileScanData;
import ports.mekanism.lib.event.entity.EntityAttributeCreationEvent;
import ports.mekanism.lib.registries.ForgeRegistries;
import ports.mekanism.lib.registries.IForgeRegistry;
import ports.mekanism.lib.registries.NewRegistryEvent;
import ports.mekanism.lib.registries.RegisterEvent;
import ports.mekanism.lib.registries.RegistryManager;
import ports.mekanism.lib.registries.SimpleForgeRegistry;
import ports.mekanism.lib.registries.VanillaForgeRegistry;
import ports.mekanism.platform.Platform;

/**
 * Drives the port's mods through Forge's loading stages: construction, registry creation, registration, config
 * loading, common setup, client setup and inter-mod messages.
 */
public final class FabricLifecycle {

    private record OwnMod(String modId, Supplier<Object> constructor) {
    }

    private static final List<OwnMod> MODS = new ArrayList<>();
    private static final List<ModContainer> CONTAINERS = new ArrayList<>();

    private FabricLifecycle() {
    }

    /**
     * Adds one of the port's mods. Mods are constructed in the order they are added.
     */
    static void addMod(String modId, Supplier<Object> constructor) {
        MODS.add(new OwnMod(modId, constructor));
    }

    static void startCommon() {
        for (OwnMod mod : MODS) {
            ModContainer container = ModList.get().addOwnContainer(mod.modId());
            ModLoadingContext.get().setActiveContainer(container);
            try {
                mod.constructor().get();
            } finally {
                ModLoadingContext.get().setActiveContainer(null);
            }
            CONTAINERS.add(container);
        }
        registerAutomaticSubscribers();
        postToMods(new NewRegistryEvent(builder -> Platform.backend().createRegistry(builder)));
        registerContent();
        FabricTransfer.registerBlockEntityLookups(MODS.stream().map(OwnMod::modId).collect(Collectors.toSet()));
        postToMods(new EntityAttributeCreationEvent());
        if (Platform.dist() == Dist.CLIENT) {
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, FMLPaths.CONFIGDIR.get());
        }
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
        forEachMod(container -> container.getEventBus().post(new FMLCommonSetupEvent(container)));
        if (Platform.dist() == Dist.DEDICATED_SERVER) {
            finishLoading();
        }
    }

    public static void startClient() {
        forEachMod(container -> container.getEventBus().post(new FMLClientSetupEvent(container)));
        finishLoading();
    }

    public static void postToMods(Event event) {
        for (ModContainer container : CONTAINERS) {
            container.getEventBus().post(event);
        }
    }

    public static void forEachMod(Consumer<ModContainer> action) {
        CONTAINERS.forEach(action);
    }

    /**
     * Registers the classes annotated with {@link Mod.EventBusSubscriber} for the running side, as Forge's
     * {@code AutomaticEventSubscriber} does before any registration event.
     */
    private static void registerAutomaticSubscribers() {
        Type subscriberType = Type.getType(Mod.EventBusSubscriber.class);
        String dist = Platform.dist().name();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
                if (!annotation.annotationType().equals(subscriberType)) {
                    continue;
                }
                Map<String, Object> values = annotation.annotationData();
                List<?> dists = (List<?>) values.get("value");
                if (dists != null && dists.stream().noneMatch(holder -> ((ModAnnotation.EnumHolder) holder).getValue().equals(dist))) {
                    continue;
                }
                String modId = (String) values.getOrDefault("modid", "");
                ModContainer container = CONTAINERS.stream()
                      .filter(candidate -> modId.isEmpty() || candidate.getModId().equals(modId))
                      .findFirst()
                      .orElseThrow(() -> new IllegalStateException("No mod " + modId + " for subscriber " + annotation.clazz().getClassName()));
                Class<?> subscriber;
                try {
                    subscriber = Class.forName(annotation.clazz().getClassName(), true, FabricLifecycle.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Failed to load event subscriber " + annotation.clazz().getClassName(), e);
                }
                ModAnnotation.EnumHolder bus = (ModAnnotation.EnumHolder) values.get("bus");
                if (bus != null && bus.getValue().equals(Mod.EventBusSubscriber.Bus.MOD.name())) {
                    container.getEventBus().register(subscriber);
                } else {
                    MinecraftForge.EVENT_BUS.register(subscriber);
                }
            }
        }
    }

    private static void finishLoading() {
        forEachMod(container -> container.getEventBus().post(new InterModEnqueueEvent(container)));
        forEachMod(container -> container.getEventBus().post(new InterModProcessEvent(container)));
    }

    /**
     * Fires one {@link RegisterEvent} per registry. Fluid types, fluids, blocks and items go first, in that order,
     * because the port's buckets and liquid blocks look their fluid up when they are created; the rest follow sorted
     * by name, as on Forge. Configured and placed features live in {@link BuiltinRegistries} and are already frozen, so
     * they are opened, filled, and frozen again after the static registries.
     */
    private static void registerContent() {
        Map<ResourceKey<? extends Registry<?>>, RegisterEvent.Registrar> registrars = new LinkedHashMap<>();
        registrars.put(ForgeRegistries.Keys.FLUID_TYPES, (name, value) -> FluidTypes.REGISTRY.register(name, cast(value)));
        registrars.put(Registry.FLUID_REGISTRY, vanilla(Registry.FLUID));
        registrars.put(Registry.BLOCK_REGISTRY, vanilla(Registry.BLOCK));
        registrars.put(Registry.ITEM_REGISTRY, vanilla(Registry.ITEM));

        Map<ResourceKey<? extends Registry<?>>, RegisterEvent.Registrar> rest = new LinkedHashMap<>();
        for (Registry<?> registry : Registry.REGISTRY) {
            rest.putIfAbsent(registry.key(), vanilla(registry));
        }
        RegisterEvent.Registrar serializers = forgeOnly(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS);
        rest.put(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, (name, value) -> {
            serializers.register(name, value);
            EntityDataSerializers.registerSerializer((EntityDataSerializer<?>) value);
        });
        rest.put(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, forgeOnly(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS));
        rest.put(ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, forgeOnly(ForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS));
        rest.keySet().removeAll(registrars.keySet());
        rest.entrySet().stream()
              .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
              .forEachOrdered(entry -> registrars.put(entry.getKey(), entry.getValue()));

        registrars.forEach((key, registrar) -> postToMods(new RegisterEvent(key, registrar)));
        registerWorldgen();
    }

    /**
     * Configured and placed features are not in {@link Registry#REGISTRY}. Forge still fires their registration events.
     * Configured features are registered first: a placed feature holds the configured feature's registry holder.
     */
    private static void registerWorldgen() {
        registerWorldgen(BuiltinRegistries.CONFIGURED_FEATURE);
        registerWorldgen(BuiltinRegistries.PLACED_FEATURE);
    }

    private static void registerWorldgen(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mapped) {
            unfreeze(mapped);
        }
        RegistryManager.ACTIVE.add(new VanillaForgeRegistry<>(registry));
        postToMods(new RegisterEvent(registry.key(), vanilla(registry)));
        registry.freeze();
    }

    private static <T> void unfreeze(MappedRegistry<T> registry) {
        registry.frozen = false;
    }

    private static RegisterEvent.Registrar vanilla(Registry<?> registry) {
        return (name, value) -> Registry.register(FabricLifecycle.<Registry<Object>>cast(registry), name, value);
    }

    /**
     * Registries Forge adds that have no vanilla counterpart; the port keeps their entries for its own adapters.
     */
    private static <T> RegisterEvent.Registrar forgeOnly(ResourceKey<Registry<T>> key) {
        IForgeRegistry<T> registry = RegistryManager.ACTIVE.getRegistry(key);
        if (registry == null) {
            registry = new SimpleForgeRegistry<>(key, null, null);
            RegistryManager.ACTIVE.add(registry);
        }
        SimpleForgeRegistry<T> simple = (SimpleForgeRegistry<T>) registry;
        return (name, value) -> simple.register(name, cast(value));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
