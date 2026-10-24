package ports.gridwright.forge;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Forge services: deferred registers on the mod bus, extended menus, Forge fuel times and Forge Energy. */
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
            DeferredRegister<?> created = DeferredRegister.create((ResourceKey) key, Gridwright.MOD_ID);
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
    public int burnTime(ItemStack stack) {
        return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
    }

    @Override
    public long pushEnergy(ServerLevel level, BlockPos target, Direction face, long amount) {
        BlockEntity neighbour = level.getBlockEntity(target);
        if (neighbour == null) return 0;
        return neighbour.getCapability(ForgeCapabilities.ENERGY, face)
                .map(storage -> storage.canReceive() ? (long) storage.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), false) : 0L)
                .orElse(0L);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        NetworkHooks.openScreen(player, provider, data);
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.get(level, FAKE_PROFILE);
    }

    @Override
    public boolean mayUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        // The placement itself fires BlockEvent.EntityPlaceEvent (ForgeHooks.onPlaceItemIntoWorld).
        return true;
    }

    @Override
    public long bucketVolume() {
        return FluidType.BUCKET_VOLUME;
    }

    @Override
    public boolean interactWithFluidStorage(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, side);
    }

    private static final GameProfile FAKE_PROFILE =
            new GameProfile(UUID.fromString("5f0c3e1a-8d2b-4c6e-9a47-6b1d2e3f4a5b"), "[Gridwright]");

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        FakePlayer player = FakePlayerFactory.get(level, profile);
        // The factory keys players by profile only, so a player made in another dimension is moved here.
        if (player.level != level) player.setLevel(level);
        return player;
    }

    @Override
    public boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
    }

    @Override
    public void afterBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ServerPlayer player) {
        // Forge's BreakEvent is the only break hook; there is no separate after-break event.
    }
}
