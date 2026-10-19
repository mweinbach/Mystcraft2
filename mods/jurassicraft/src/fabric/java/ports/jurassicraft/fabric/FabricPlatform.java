package ports.jurassicraft.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.Platform;
import ports.jurassicraft.platform.SpawnEggBehavior;

/**
 * Fabric registers natively, but entries are queued and flushed in Forge's dependency order so shared
 * registry classes resolve the same way on both loaders.
 */
final class FabricPlatform implements Platform.Backend, Network.Transport {
    /** Installed by the client entrypoint; dedicated servers never send serverbound packets. */
    static BiFunction<ResourceLocation, FriendlyByteBuf, Packet<?>> serverbound;
    private static final List<ResourceLocation> FIRST = List.of(Registry.SOUND_EVENT_REGISTRY.location(), Registry.BLOCK_REGISTRY.location(),
            Registry.FLUID_REGISTRY.location(), Registry.ENTITY_TYPE_REGISTRY.location(), Registry.ITEM_REGISTRY.location());
    private final Map<ResourceLocation, List<Pending<?>>> pending = new LinkedHashMap<>();
    private boolean flushed;

    private static final class Pending<T> implements Supplier<T> {
        private final Registry<? super T> registry;
        private final ResourceLocation id;
        private final Supplier<T> factory;
        private T value;
        Pending(Registry<? super T> registry, ResourceLocation id, Supplier<T> factory) { this.registry = registry; this.id = id; this.factory = factory; }
        @SuppressWarnings("unchecked")
        void register() {
            T created = factory.get();
            // Registering a PoiType directly leaves its block states unmapped, so villagers never find them.
            value = created instanceof PoiType poi
                    ? (T) PointOfInterestHelper.register(id, poi.maxTickets(), poi.validRange(), poi.matchingStates())
                    : Registry.register(registry, id, created);
        }
        @Override public T get() { return Objects.requireNonNull(value, () -> "Registry entry " + id + " is not registered yet"); }
    }

    FabricPlatform() { for (ResourceLocation registry : FIRST) pending.put(registry, new ArrayList<>()); }

    @Override @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        if (flushed) throw new IllegalStateException("JurassiCraft registration is closed: " + name);
        Registry<? super T> target = (Registry<? super T>) Registry.REGISTRY.get(registry.location());
        if (target == null) throw new IllegalArgumentException("Unknown registry " + registry.location());
        Pending<T> entry = new Pending<>(target, new ResourceLocation(PortMod.MOD_ID, name), factory);
        pending.computeIfAbsent(registry.location(), key -> new ArrayList<>()).add(entry);
        return entry;
    }

    /** Registers every queued entry; later registry groups may depend on earlier ones. */
    void flush() {
        flushed = true;
        for (List<Pending<?>> entries : pending.values()) for (Pending<?> entry : entries) entry.register();
        pending.clear();
    }

    @Override public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>(factory::create);
    }
    @Override public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buffer) { openingData.accept(buffer); }
            @Override public Component getDisplayName() { return provider.getDisplayName(); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) { return provider.createMenu(id, inventory, opener); }
        });
    }
    @Override public CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon) {
        return FabricItemGroupBuilder.build(new ResourceLocation(PortMod.MOD_ID, name), icon);
    }
    @Override public Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Item.Properties properties,
            @Nullable SpawnEggBehavior behavior) {
        if (behavior == null) return new SpawnEggItem(type.get(), background, highlight, properties);
        return new SpawnEggItem(type.get(), background, highlight, properties) {
            @Override public InteractionResult useOn(UseOnContext context) { return behavior.useOn(this, context); }
            @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                return behavior.use(this, level, player, hand);
            }
            @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
                super.appendHoverText(stack, level, lines, flag);
                behavior.appendHoverText(this, stack, level, lines, flag);
            }
        };
    }
    @Override public Item recordItem(int comparatorOutput, Supplier<SoundEvent> sound, Item.Properties properties, int lengthTicks) {
        return new RecordItem(comparatorOutput, sound.get(), properties, (lengthTicks + 19) / 20) {
            @Override public int getLengthInTicks() { return lengthTicks; }
        };
    }
    @Override public boolean allowTreeGrowth(LevelAccessor level, RandomSource random, BlockPos pos) { return true; }
    @Override public boolean mobGriefing(Level level, Entity entity) { return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); }
    @Override public boolean restoringBlockSnapshots(Level level) { return false; }
    @Override public FoodProperties foodProperties(ItemStack stack, @Nullable LivingEntity entity) { return stack.getItem().getFoodProperties(); }
    @Override public CompoundTag persistentData(Entity entity) { return ((PersistentDataHolder) entity).jurassicraft$persistentData(); }
    @Override public Network.Transport network() { return this; }

    @Override public Packet<?> toClient(ResourceLocation id, FriendlyByteBuf payload) { return ServerPlayNetworking.createS2CPacket(id, payload); }
    @Override public Packet<?> toServer(ResourceLocation id, FriendlyByteBuf payload) {
        if (serverbound == null) throw new IllegalStateException("Serverbound packets require a client");
        return serverbound.apply(id, payload);
    }
    @Override public boolean canSend(ServerPlayer player, ResourceLocation id) { return ServerPlayNetworking.canSend(player, id); }

    static void registerServerReceivers() {
        for (ResourceLocation id : Network.serverIds()) {
            ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, sender) -> {
                FriendlyByteBuf copy = new FriendlyByteBuf(buffer.copy());
                server.execute(() -> Network.receiveOnServer(id, player, copy));
            });
        }
    }
}
