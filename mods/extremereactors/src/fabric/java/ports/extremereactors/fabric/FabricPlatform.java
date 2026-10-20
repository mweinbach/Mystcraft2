/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.block.AbstractModBlockEntity;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;
import ports.extremereactors.platform.FluidDefinition;
import ports.extremereactors.platform.Network;
import ports.extremereactors.platform.Platform;
import ports.extremereactors.platform.registry.DeferredRegister;
import ports.extremereactors.platform.registry.RegistryObject;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Fabric implementation of the loader services.
 */
public final class FabricPlatform implements Platform.Backend {

    /**
     * The services only the physical client can provide, installed by the client entrypoint.
     */
    public interface ClientSide {

        void registerReceiver(ResourceLocation channel, Network.ClientReceiver receiver);

        void sendToServer(ResourceLocation channel, FriendlyByteBuf data);

        Optional<Level> level();

        Optional<Player> player();

        @Nullable RecipeManager recipeManager();
    }

    FabricPlatform() {

        this._registers = new ArrayList<>();
        this._blocksWithEntities = new LinkedHashSet<>();
        this._fluids = new IdentityHashMap<>();
        this._levelTickHandlers = new CopyOnWriteArrayList<>();
        this._levelUnloadHandlers = new CopyOnWriteArrayList<>();
        this._network = new FabricNetwork();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> this._server = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> this._server = null);
        ServerTickEvents.START_WORLD_TICK.register(this::fireLevelTickStart);
        ServerWorldEvents.UNLOAD.register((server, level) -> this.fireLevelUnload(level));
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(FabricPlatform::onBlockEntityUnloaded);
    }

    public static FabricPlatform get() {
        return (FabricPlatform) Platform.backend();
    }

    public void installClientSide(final ClientSide client) {

        this._client = client;
        this._network.installClientSide(client);
    }

    /**
     * Create and register every object the content classes declared, registry by registry in vanilla order so that,
     * for example, fluids exist before the blocks and buckets that refer to them.
     */
    void registerAll() {

        for (final Registry<?> registry : Registry.REGISTRY) {
            for (final DeferredRegister<?> register : this._registers) {
                if (register.getRegistryKey().equals(registry.key())) {
                    registerEntries(registry, register);
                }
            }
        }

        this._registers.clear();
        FabricTransfer.registerBlockLookups(this._blocksWithEntities.toArray(new Block[0]));
    }

    public Map<Fluid, FluidDefinition> getFluids() {
        return Collections.unmodifiableMap(this._fluids);
    }

    public void fireLevelTickStart(final Level level) {

        for (final Consumer<Level> handler : this._levelTickHandlers) {
            handler.accept(level);
        }
    }

    public void fireLevelUnload(final LevelAccessor level) {

        for (final Consumer<LevelAccessor> handler : this._levelUnloadHandlers) {
            handler.accept(level);
        }
    }

    public static void onBlockEntityUnloaded(final BlockEntity blockEntity, final Level level) {

        if (blockEntity instanceof AbstractModBlockEntity modEntity) {
            modEntity.onChunkUnloaded();
        }
    }

    //region Platform.Backend

    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isPhysicalClient() {
        return EnvType.CLIENT == FabricLoader.getInstance().getEnvironmentType();
    }

    @Nullable
    @Override
    public MinecraftServer currentServer() {
        return this._server;
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(final String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** Team Reborn Energy's unit, as Tech Reborn shows it. */
    @Override
    public String energyUnit() {
        return "E";
    }

    @Override
    public String energyUnitName() {
        return "Energy";
    }

    @Override
    public void addClientReloadListener(final Runnable listener) {

        final ResourceLocation id = new ResourceLocation(Platform.MOD_ID, "reload_listener_" + this._reloadListenersCount++);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {

            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public void onResourceManagerReload(final ResourceManager resourceManager) {
                listener.run();
            }
        });
    }

    @Nullable
    @Override
    public RecipeManager clientRecipeManager() {
        return null != this._client ? this._client.recipeManager() : null;
    }

    @Override
    public void submitRegister(final DeferredRegister<?> register) {
        this._registers.add(register);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(final Platform.BlockEntityFactory<T> factory,
                                                                     final Block... validBlocks) {

        this._blocksWithEntities.addAll(Arrays.asList(validBlocks));
        return FabricBlockEntityTypeBuilder.create(factory::create, validBlocks).build();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(final Platform.MenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>(factory::create);
    }

    @Override
    public void openMenu(final ServerPlayer player, final MenuProvider provider, final Consumer<FriendlyByteBuf> extraData) {
        player.openMenu(new ExtendedScreenHandlerFactory() {

            @Override
            public void writeScreenOpeningData(final ServerPlayer target, final FriendlyByteBuf buffer) {
                extraData.accept(buffer);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(final int windowId, final Inventory inventory, final Player target) {
                return provider.createMenu(windowId, inventory, target);
            }
        });
    }

    @Override
    public CreativeModeTab creativeTab(final ResourceLocation id, final Supplier<ItemStack> icon,
                                       @Nullable final Supplier<List<ItemStack>> contents) {

        final FabricItemGroupBuilder builder = FabricItemGroupBuilder.create(id).icon(icon);

        if (null != contents) {
            builder.appendItems(stacks -> stacks.addAll(contents.get()));
        }

        return builder.build();
    }

    @Override
    public void declareFluid(final FluidDefinition definition) {
    }

    @Override
    public FlowingFluid fluid(final FluidDefinition definition, final boolean source,
                              final Supplier<? extends FlowingFluid> sourceFluid,
                              final Supplier<? extends FlowingFluid> flowingFluid,
                              final Supplier<? extends Item> bucket, final Supplier<? extends LiquidBlock> block) {

        final FlowingFluid fluid = FabricFluid.create(definition, source, sourceFluid, flowingFluid, bucket, block);

        this._fluids.put(fluid, definition);

        if (source) {
            FluidVariantAttributes.register(fluid, new FabricFluid.Attributes(definition));
        }

        return fluid;
    }

    @Override
    public Component fluidName(final Fluid fluid) {
        return FluidVariantAttributes.getName(variantOf(fluid));
    }

    @Override
    public int fluidTemperature(final Fluid fluid) {
        return FluidVariantAttributes.getTemperature(variantOf(fluid));
    }

    @Override
    public boolean isFluidLighterThanAir(final Fluid fluid) {
        return FluidVariantAttributes.isLighterThanAir(variantOf(fluid));
    }

    @Override
    public <T> LazyOptional<T> foreignCapability(final Level level, final BlockPos position, final BlockState state,
                                                 @Nullable final BlockEntity blockEntity, final Capability<T> capability,
                                                 @Nullable final Direction side) {
        return FabricTransfer.foreignCapability(level, position, state, blockEntity, capability, side);
    }

    @Nullable
    @Override
    public IEnergyStorage itemEnergy(final IItemHandlerModifiable inventory, final int slot) {
        return FabricEnergy.itemEnergy(inventory, slot);
    }

    @Override
    public boolean interactWithFluidHandler(final Player player, final InteractionHand hand, final IFluidHandler handler) {
        return FluidStorageUtil.interactWithFluidStorage(FabricTransfer.asStorage(handler), player, hand);
    }

    @Override
    public void onLevelTickStart(final Consumer<Level> handler) {
        this._levelTickHandlers.add(handler);
    }

    @Override
    public void onLevelUnload(final Consumer<LevelAccessor> handler) {
        this._levelUnloadHandlers.add(handler);
    }

    @Override
    public void onServerStarted(final Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STARTED.register(handler::accept);
    }

    @Override
    public void onServerStopped(final Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STOPPED.register(handler::accept);
    }

    @Override
    public void onRegisterCommands(final Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> handler.accept(dispatcher));
    }

    @Override
    public void onTagsUpdated(final Runnable handler) {
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> handler.run());
    }

    @Override
    public Network network() {
        return this._network;
    }

    //endregion
    //region internals

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerEntries(final Registry registry, final DeferredRegister<?> register) {

        for (final RegistryObject<?> entry : register.getEntries()) {
            Registry.register(registry, ResourceKey.create(registry.key(), entry.getId()), entry.create());
        }
    }

    private static FluidVariant variantOf(final Fluid fluid) {
        return FluidVariant.of(fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid);
    }

    private final List<DeferredRegister<?>> _registers;
    private final Set<Block> _blocksWithEntities;
    private final Map<Fluid, FluidDefinition> _fluids;
    private final List<Consumer<Level>> _levelTickHandlers;
    private final List<Consumer<LevelAccessor>> _levelUnloadHandlers;
    private final FabricNetwork _network;
    private int _reloadListenersCount;
    @Nullable
    private volatile MinecraftServer _server;
    @Nullable
    private ClientSide _client;

    //endregion
}
