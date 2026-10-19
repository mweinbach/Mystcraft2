package com.technovision.advancedgenetics.platform;

import com.mojang.brigadier.arguments.ArgumentType;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Loader services used by shared gameplay. Each loader entrypoint installs its backend before any
 * registry class initializes, so every block, item, menu and rule is defined once in shared code.
 */
public final class Platform {

    public interface Backend {
        <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory);
        <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);
        /** Opens a machine menu; the client's menu factory reads {@code pos} from the opening data. */
        void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos);
        /** A creative tab whose translation key is {@code itemGroup.<namespace>.<path>}. */
        CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon);
        <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void registerArgumentType(String name, Class<A> type, ArgumentTypeInfo<A, T> info);
        PlayerGeneticsComponent genetics(Player player);
        /** True for loader-provided stand-in players, which have no client to sync. */
        boolean isFakePlayer(ServerPlayer player);
        void sendToPlayer(ServerPlayer player, SyncGeneticsPacket packet);
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }

    private static Backend backend;

    private Platform() { }

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("Advanced Genetics platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    private static Backend backend() {
        if (backend == null) throw new IllegalStateException("Advanced Genetics platform is not installed");
        return backend;
    }

    public static <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        return backend().register(registry, name, factory);
    }

    public static <M extends AbstractContainerMenu> Supplier<MenuType<M>> menu(String name, MenuFactory<M> factory) {
        return register(Registry.MENU_REGISTRY, name, () -> backend().menuType(factory));
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        backend().openMenu(player, provider, pos);
    }

    public static CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon) {
        return backend().creativeTab(id, icon);
    }

    public static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void registerArgumentType(String name, Class<A> type, ArgumentTypeInfo<A, T> info) {
        backend().registerArgumentType(name, type, info);
    }

    public static PlayerGeneticsComponent genetics(Player player) {
        return backend().genetics(player);
    }

    public static boolean isFakePlayer(ServerPlayer player) {
        return backend().isFakePlayer(player);
    }

    public static void sendToPlayer(ServerPlayer player, SyncGeneticsPacket packet) {
        backend().sendToPlayer(player, packet);
    }
}
