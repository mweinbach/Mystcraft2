/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Forge implementation of the loader services.
 */
public final class ForgePlatform implements Platform.Backend {

    /**
     * The services only the physical client can provide, installed by the client setup.
     */
    public interface ClientSide {

        Optional<Level> level();

        Optional<Player> player();

        @Nullable RecipeManager recipeManager();
    }

    ForgePlatform(final IEventBus modBus) {

        this._registers = new CopyOnWriteArrayList<>();
        this._fluidTypes = new IdentityHashMap<>();
        this._clientReloadListeners = new CopyOnWriteArrayList<>();
        this._network = new ForgeNetwork();

        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, this::onRegister);
    }

    public static ForgePlatform get() {
        return (ForgePlatform) Platform.backend();
    }

    public void installClientSide(final ClientSide client) {

        this._client = client;
        this._network.installClientSide(client);
    }

    /**
     * The listeners added with {@link #addClientReloadListener}, for the client setup to register with Forge.
     */
    public List<Runnable> getClientReloadListeners() {
        return this._clientReloadListeners;
    }

    //region Platform.Backend

    @Override
    public String loaderName() {
        return "Forge";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Nullable
    @Override
    public MinecraftServer currentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(final String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String energyUnit() {
        return "FE";
    }

    @Override
    public String energyUnitName() {
        return "Forge Energy";
    }

    @Override
    public void addClientReloadListener(final Runnable listener) {
        this._clientReloadListeners.add(listener);
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
        //noinspection ConstantConditions
        return BlockEntityType.Builder.of(factory::create, validBlocks).build(null);
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(final Platform.MenuFactory<M> factory) {
        return IForgeMenuType.create(factory::create);
    }

    @Override
    public void openMenu(final ServerPlayer player, final MenuProvider provider, final Consumer<FriendlyByteBuf> extraData) {
        NetworkHooks.openScreen(player, provider, extraData);
    }

    @Override
    public CreativeModeTab creativeTab(final ResourceLocation id, final Supplier<ItemStack> icon,
                                       @Nullable final Supplier<List<ItemStack>> contents) {
        return new CreativeModeTab(id.getNamespace() + "." + id.getPath()) {

            @Override
            public ItemStack makeIcon() {
                return icon.get();
            }

            @Override
            public void fillItemList(final NonNullList<ItemStack> items) {

                if (null != contents) {
                    items.addAll(contents.get());
                } else {
                    super.fillItemList(items);
                }
            }
        };
    }

    @Override
    public void declareFluid(final FluidDefinition definition) {
        this._fluidTypes.put(definition, createFluidType(definition));
    }

    @Override
    public FlowingFluid fluid(final FluidDefinition definition, final boolean source,
                              final Supplier<? extends FlowingFluid> sourceFluid,
                              final Supplier<? extends FlowingFluid> flowingFluid,
                              final Supplier<? extends Item> bucket, final Supplier<? extends LiquidBlock> block) {

        final FluidType type = Objects.requireNonNull(this._fluidTypes.get(definition),
                () -> "Fluid " + definition.name() + " was not declared");
        final ForgeFlowingFluid.Properties properties = new ForgeFlowingFluid.Properties(() -> type, sourceFluid, flowingFluid)
                .bucket(bucket)
                .block(block);

        return source ? new ForgeFlowingFluid.Source(properties) : new ForgeFlowingFluid.Flowing(properties);
    }

    @Override
    public Component fluidName(final Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }

    @Override
    public int fluidTemperature(final Fluid fluid) {
        return fluid.getFluidType().getTemperature();
    }

    @Override
    public boolean isFluidLighterThanAir(final Fluid fluid) {
        return fluid.getFluidType().isLighterThanAir();
    }

    @Override
    public <T> LazyOptional<T> foreignCapability(final Level level, final BlockPos position, final BlockState state,
                                                 @Nullable final BlockEntity blockEntity, final Capability<T> capability,
                                                 @Nullable final Direction side) {
        return ForgeTransfer.foreignCapability(blockEntity, capability, side);
    }

    @Nullable
    @Override
    public IEnergyStorage itemEnergy(final IItemHandlerModifiable inventory, final int slot) {
        return ForgeTransfer.itemEnergy(inventory, slot);
    }

    @Override
    public boolean interactWithFluidHandler(final Player player, final InteractionHand hand, final IFluidHandler handler) {
        return ForgeTransfer.interactWithFluidHandler(player, hand, handler);
    }

    @Override
    public void onLevelTickStart(final Consumer<Level> handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.LevelTickEvent.class, event -> {

            if (TickEvent.Phase.START == event.phase) {
                handler.accept(event.level);
            }
        });
    }

    @Override
    public void onLevelUnload(final Consumer<LevelAccessor> handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LevelEvent.Unload.class,
                event -> handler.accept(event.getLevel()));
    }

    @Override
    public void onServerStarted(final Consumer<MinecraftServer> handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartedEvent.class,
                event -> handler.accept(event.getServer()));
    }

    @Override
    public void onServerStopped(final Consumer<MinecraftServer> handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class,
                event -> handler.accept(event.getServer()));
    }

    @Override
    public void onRegisterCommands(final Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterCommandsEvent.class,
                event -> handler.accept(event.getDispatcher()));
    }

    @Override
    public void onTagsUpdated(final Runnable handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TagsUpdatedEvent.class, event -> handler.run());
    }

    @Override
    public Network network() {
        return this._network;
    }

    //endregion
    //region internals

    /**
     * Forge fires one event per registry, blocks and items before fluids: our liquid blocks and buckets resolve
     * their fluid lazily.
     */
    private void onRegister(final RegisterEvent event) {

        if (event.getRegistryKey().equals(ForgeRegistries.Keys.FLUID_TYPES)) {

            event.register(ForgeRegistries.Keys.FLUID_TYPES, helper -> this._fluidTypes.forEach((definition, type) ->
                    helper.register(new ResourceLocation(Platform.MOD_ID, definition.typeName()), type)));
            return;
        }

        for (final DeferredRegister<?> register : this._registers) {
            if (register.getRegistryKey().equals(event.getRegistryKey())) {
                registerEntries(event, register);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerEntries(final RegisterEvent event, final DeferredRegister<?> register) {
        event.register((net.minecraft.resources.ResourceKey) register.getRegistryKey(), helper -> {

            for (final RegistryObject<?> entry : register.getEntries()) {
                ((RegisterEvent.RegisterHelper) helper).register(entry.getId(), entry.create());
            }
        });
    }

    /**
     * The same fluid properties Extreme Reactors gives its reactant fluids and steam.
     */
    private static FluidType createFluidType(final FluidDefinition definition) {

        final boolean swimmable = definition.swimmable();
        final FluidType.Properties properties = FluidType.Properties.create()
                .descriptionId(definition.descriptionId())
                .density(definition.density())
                .temperature(definition.temperature())
                .viscosity(definition.viscosity())
                .lightLevel(definition.lightLevel())
                .rarity(definition.rarity())
                .canDrown(swimmable)
                .canSwim(swimmable)
                .canPushEntity(swimmable)
                .fallDistanceModifier(swimmable ? 0.4f : 0.1f)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(swimmable)
                .pathType(swimmable ? BlockPathTypes.WATER : BlockPathTypes.WALKABLE)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                .canHydrate(false);

        return new FluidType(properties) {

            @Override
            public void initializeClient(final Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {

                    @Override
                    public ResourceLocation getStillTexture() {
                        return definition.stillTexture();
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return definition.flowingTexture();
                    }

                    @Override
                    public ResourceLocation getOverlayTexture() {
                        return definition.overlayTexture();
                    }

                    @Override
                    public int getTintColor() {
                        return definition.tint();
                    }
                });
            }
        };
    }

    private final List<DeferredRegister<?>> _registers;
    private final Map<FluidDefinition, FluidType> _fluidTypes;
    private final List<Runnable> _clientReloadListeners;
    private final ForgeNetwork _network;
    @Nullable
    private ClientSide _client;

    //endregion
}
