package com.technovision.advancedgenetics.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.network.ModNetwork;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import com.technovision.advancedgenetics.platform.Platform;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fabric registers natively, but entries are queued and flushed blocks first, then items, then the
 * rest, as Forge's registry events do, so shared suppliers resolve the same way on both loaders.
 */
final class FabricPlatform implements Platform.Backend {

    private static final List<ResourceLocation> FIRST = List.of(Registry.BLOCK_REGISTRY.location(), Registry.ITEM_REGISTRY.location());
    private final Map<ResourceLocation, List<Pending<?>>> pending = new LinkedHashMap<>();
    private boolean flushed;

    FabricPlatform() {
        for (ResourceLocation registry : FIRST) pending.put(registry, new ArrayList<>());
    }

    private static final class Pending<T> implements Supplier<T> {
        private final Registry<? super T> registry;
        private final ResourceLocation id;
        private final Supplier<T> factory;
        private T value;

        Pending(Registry<? super T> registry, ResourceLocation id, Supplier<T> factory) {
            this.registry = registry;
            this.id = id;
            this.factory = factory;
        }

        void register() {
            value = Registry.register(registry, id, factory.get());
        }

        @Override
        public T get() {
            return Objects.requireNonNull(value, () -> "Registry entry " + id + " is not registered yet");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        if (flushed) throw new IllegalStateException("Advanced Genetics registration is closed: " + name);
        Registry<? super T> target = (Registry<? super T>) Registry.REGISTRY.get(registry.location());
        if (target == null) throw new IllegalArgumentException("Unknown registry " + registry.location());
        Pending<T> entry = new Pending<>(target, new ResourceLocation(AdvancedGenetics.MOD_ID, name), factory);
        pending.computeIfAbsent(registry.location(), key -> new ArrayList<>()).add(entry);
        return entry;
    }

    /** Registers every queued entry; later registry groups may depend on earlier ones. */
    void flush() {
        flushed = true;
        for (List<Pending<?>> entries : pending.values()) {
            for (Pending<?> entry : entries) entry.register();
        }
        pending.clear();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buf) {
                buf.writeBlockPos(pos);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return provider.createMenu(containerId, inventory, opener);
            }
        });
    }

    @Override
    public CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon) {
        return FabricItemGroupBuilder.build(id, icon);
    }

    @Override
    public <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void registerArgumentType(String name, Class<A> type, ArgumentTypeInfo<A, T> info) {
        ArgumentTypeRegistry.registerArgumentType(new ResourceLocation(AdvancedGenetics.MOD_ID, name), type, info);
    }

    @Override
    public PlayerGeneticsComponent genetics(Player player) {
        return ((GeneticsHolder) player).advancedgenetics$genetics();
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return false;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, SyncGeneticsPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        SyncGeneticsPacket.encode(packet, buf);
        ServerPlayNetworking.send(player, ModNetwork.SYNC_GENETICS, buf);
    }
}
