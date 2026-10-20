/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;
import ports.extremereactors.platform.registry.DeferredRegister;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The loader services the shared code needs. Each loader entrypoint installs a {@link Backend} before any content
 * class is initialized.
 */
public final class Platform {

    public static final String MOD_ID = "bigreactors";

    public interface Backend {

        String loaderName();

        boolean isDevelopmentEnvironment();

        boolean isPhysicalClient();

        @Nullable MinecraftServer currentServer();

        Path configDirectory();

        boolean isModLoaded(String modId);

        void addClientReloadListener(Runnable listener);

        /** The recipes the client received from the server, or null on a dedicated server or outside a world. */
        @Nullable RecipeManager clientRecipeManager();

        //region registration

        void submitRegister(DeferredRegister<?> register);

        <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... validBlocks);

        <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);

        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraData);

        /**
         * @param id the tab id; its title is the {@code itemGroup.<namespace>.<path>} translation
         * @param contents the stacks listed in the tab, in order, or null to list the items that declare the tab
         */
        CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon,
                                    @Nullable Supplier<List<ItemStack>> contents);

        //endregion
        //region fluids

        FlowingFluid fluid(FluidDefinition definition, boolean source, Supplier<? extends FlowingFluid> sourceFluid,
                           Supplier<? extends FlowingFluid> flowingFluid, Supplier<? extends Item> bucket,
                           Supplier<? extends LiquidBlock> block);

        LiquidBlock liquidBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties);

        Component fluidName(Fluid fluid);

        /** In Kelvin. */
        int fluidTemperature(Fluid fluid);

        boolean isFluidLighterThanAir(Fluid fluid);

        //endregion
        //region capabilities

        /**
         * Look up a handler exposed by a block (or block entity) that is not one of ours, adapting the loader's
         * native transfer API to the corelib interfaces. Our own block entities are queried directly by
         * {@link ports.extremereactors.corelib.capability.CapabilityHelper}.
         */
        <T> LazyOptional<T> foreignCapability(Level level, BlockPos position, BlockState state,
                                              @Nullable BlockEntity blockEntity, Capability<T> capability,
                                              @Nullable Direction side);

        /**
         * An energy view of the item in a slot, or null if that item cannot hold energy. Changes made through the
         * view are written back to the slot.
         */
        @Nullable IEnergyStorage itemEnergy(IItemHandlerModifiable inventory, int slot);

        /**
         * Let the player fill or empty the held fluid container with the given handler.
         *
         * @return true if some fluid was moved
         */
        boolean interactWithFluidHandler(Player player, InteractionHand hand, IFluidHandler handler);

        //endregion
        //region events

        void onLevelTickStart(Consumer<Level> handler);

        void onLevelUnload(Consumer<LevelAccessor> handler);

        void onServerStarted(Consumer<MinecraftServer> handler);

        void onServerStopped(Consumer<MinecraftServer> handler);

        void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler);

        /** Fired on both logical sides after tags are (re)loaded or synchronized. */
        void onTagsUpdated(Runnable handler);

        //endregion

        Network network();
    }

    @FunctionalInterface
    public interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos position, BlockState state);
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int windowId, Inventory inventory, FriendlyByteBuf data);
    }

    public static void install(final Backend implementation) {

        if (null != s_backend) {
            throw new IllegalStateException("The Extreme Reactors platform backend is already installed");
        }

        s_backend = Objects.requireNonNull(implementation);
    }

    public static Backend backend() {

        if (null == s_backend) {
            throw new IllegalStateException("The Extreme Reactors platform backend is not installed");
        }

        return s_backend;
    }

    public static boolean isInstalled() {
        return null != s_backend;
    }

    //region shortcuts

    public static String activeModId() {
        return MOD_ID;
    }

    public static String loaderName() {
        return backend().loaderName();
    }

    public static boolean isDevelopmentEnvironment() {
        return backend().isDevelopmentEnvironment();
    }

    public static boolean isPhysicalClient() {
        return backend().isPhysicalClient();
    }

    @Nullable
    public static MinecraftServer currentServer() {
        return backend().currentServer();
    }

    public static Path configDirectory() {
        return backend().configDirectory();
    }

    public static boolean isModLoaded(final String modId) {
        return backend().isModLoaded(modId);
    }

    public static void addClientReloadListener(final Runnable listener) {
        backend().addClientReloadListener(listener);
    }

    public static void submitRegister(final DeferredRegister<?> register) {
        backend().submitRegister(register);
    }

    public static Network network() {
        return backend().network();
    }

    //endregion

    private static Backend s_backend;

    private Platform() {
    }
}
