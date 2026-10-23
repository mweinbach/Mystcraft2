package ports.jurassicraft.platform;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

/**
 * Loader services used by shared gameplay. Each loader entrypoint installs its backend before any
 * registry class initializes; shared code defines every block, item, rule and packet exactly once.
 */
public final class Platform {
    public interface Backend {
        <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory);
        <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);
        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData);
        CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon);
        /** A spawn egg that resolves its type lazily; {@code behavior} is null for vanilla egg behavior. */
        Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Item.Properties properties,
                @Nullable SpawnEggBehavior behavior);
        /** A music disc whose sound may register after the item; {@code lengthTicks} is exact. */
        Item recordItem(int comparatorOutput, Supplier<SoundEvent> sound, Item.Properties properties, int lengthTicks);
        /** False when another mod vetoes a sapling turning into a tree. */
        boolean allowTreeGrowth(LevelAccessor level, RandomSource random, BlockPos pos);
        boolean mobGriefing(Level level, Entity entity);
        boolean restoringBlockSnapshots(Level level);
        /** Fire odds for a block, registered where the loader's fire lookup reads them. */
        void flammable(Block block, int burnChance, int spreadChance);
        @Nullable FoodProperties foodProperties(ItemStack stack, @Nullable LivingEntity entity);
        /** Data that persists with the entity and is never synchronized to clients. */
        CompoundTag persistentData(Entity entity);
        Network.Transport network();
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }

    private static Backend backend;

    private Platform() {}

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("JurassiCraft platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    static Backend backend() {
        if (backend == null) throw new IllegalStateException("JurassiCraft platform is not initialized");
        return backend;
    }

    public static <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        return backend().register(registry, name, factory);
    }
    public static <M extends AbstractContainerMenu> Supplier<MenuType<M>> menu(String name, MenuFactory<M> factory) {
        return register(Registry.MENU_REGISTRY, name, () -> menuType(factory));
    }
    public static <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory) { return backend().menuType(factory); }
    public static void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        backend().openMenu(player, provider, openingData);
    }
    public static void openMenu(ServerPlayer player, MenuProvider provider) {
        openMenu(player, provider, buffer -> {});
    }
    public static void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        openMenu(player, provider, buffer -> buffer.writeBlockPos(pos));
    }
    public static CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon) { return backend().creativeTab(name, icon); }
    public static Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Item.Properties properties) {
        return backend().spawnEgg(type, background, highlight, properties, null);
    }
    public static Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Item.Properties properties,
            SpawnEggBehavior behavior) {
        return backend().spawnEgg(type, background, highlight, properties, behavior);
    }
    public static Item recordItem(int comparatorOutput, Supplier<SoundEvent> sound, Item.Properties properties, int lengthTicks) {
        return backend().recordItem(comparatorOutput, sound, properties, lengthTicks);
    }
    public static boolean allowTreeGrowth(LevelAccessor level, RandomSource random, BlockPos pos) { return backend().allowTreeGrowth(level, random, pos); }
    public static boolean mobGriefing(Level level, Entity entity) { return backend().mobGriefing(level, entity); }
    public static boolean restoringBlockSnapshots(Level level) { return backend().restoringBlockSnapshots(level); }
    public static void flammable(Block block, int burnChance, int spreadChance) { backend().flammable(block, burnChance, spreadChance); }
    @Nullable public static FoodProperties foodProperties(ItemStack stack, @Nullable LivingEntity entity) { return backend().foodProperties(stack, entity); }
    public static CompoundTag persistentData(Entity entity) { return backend().persistentData(entity); }
}
