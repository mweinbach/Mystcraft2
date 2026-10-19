package com.technovision.advancedgenetics.forge;

import com.mojang.brigadier.arguments.ArgumentType;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Forge services: deferred registers on the mod bus, extended menus, capabilities and a SimpleChannel. */
final class ForgePlatform implements Platform.Backend {

    private final IEventBus modBus;
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();

    ForgePlatform(IEventBus modBus) {
        this.modBus = modBus;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        DeferredRegister register = registers.computeIfAbsent(registry, key -> {
            DeferredRegister<?> created = DeferredRegister.create((ResourceKey) key, AdvancedGenetics.MOD_ID);
            created.register(modBus);
            return created;
        });
        return (Supplier<T>) register.register(name, factory);
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return IForgeMenuType.create(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        NetworkHooks.openScreen(player, provider, pos);
    }

    @Override
    public CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon) {
        return new CreativeModeTab(id.getNamespace() + "." + id.getPath()) {
            @Override
            public ItemStack makeIcon() {
                return icon.get();
            }
        };
    }

    @Override
    public <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void registerArgumentType(String name, Class<A> type, ArgumentTypeInfo<A, T> info) {
        register(Registry.COMMAND_ARGUMENT_TYPE_REGISTRY, name, () -> ArgumentTypeInfos.registerByClass(type, info));
    }

    @Override
    public PlayerGeneticsComponent genetics(Player player) {
        return ForgeCapabilityEvents.genetics(player);
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, SyncGeneticsPacket packet) {
        ForgeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
