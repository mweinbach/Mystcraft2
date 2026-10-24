package ports.gridwright.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The loader services the shared core needs. Each loader entrypoint installs a backend before any
 * registry class initialises, so every block, item, menu and recipe is defined once in shared code.
 */
public final class Platform {

    public interface Backend {
        <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory);

        <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);

        /** Opens a machine menu; the client's menu factory reads {@code pos} from the opening data. */
        void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos);

        /** A creative tab whose translation key is {@code itemGroup.<namespace>.<path>}. */
        CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon);

        /** Furnace burn time of {@code stack} in ticks, including fuels other mods register; 0 if none. */
        int burnTime(ItemStack stack);

        /**
         * Offers {@code amount} energy units (Forge Energy or Team Reborn Energy) to the block at
         * {@code target}, entering through its {@code face}. Returns how much it accepted.
         */
        long pushEnergy(ServerLevel level, BlockPos target, Direction face, long amount);
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }

    private static Backend backend;

    private Platform() { }

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("Gridwright platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    private static Backend backend() {
        if (backend == null) throw new IllegalStateException("Gridwright platform is not installed");
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

    public static int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : backend().burnTime(stack);
    }

    public static long pushEnergy(ServerLevel level, BlockPos target, Direction face, long amount) {
        return amount <= 0 ? 0 : backend().pushEnergy(level, target, face, amount);
    }
}
