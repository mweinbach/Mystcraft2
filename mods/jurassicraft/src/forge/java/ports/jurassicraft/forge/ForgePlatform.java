package ports.jurassicraft.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.Platform;
import ports.jurassicraft.platform.SpawnEggBehavior;

/** Forge registration through deferred registers and play packets over one versioned channel. */
final class ForgePlatform implements Platform.Backend, Network.Transport {
    private static final String PROTOCOL = "2";
    private final IEventBus modBus;
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();
    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(PortMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    ForgePlatform(IEventBus modBus) {
        this.modBus = modBus;
        channel.messageBuilder(Payload.class, 0).encoder(Payload::write).decoder(Payload::read).consumerMainThread(this::receive).add();
    }

    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        DeferredRegister deferred = registers.computeIfAbsent(registry, key -> {
            DeferredRegister<?> created = DeferredRegister.create((ResourceKey) key, PortMod.MOD_ID);
            created.register(modBus);
            return created;
        });
        return deferred.register(name, factory);
    }

    @Override public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return IForgeMenuType.create(factory::create);
    }
    @Override public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        NetworkHooks.openScreen(player, provider, openingData);
    }
    @Override public CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon) {
        return new CreativeModeTab(name) {
            @Override public ItemStack makeIcon() { return icon.get(); }
        };
    }
    @Override public Item spawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Item.Properties properties,
            @Nullable SpawnEggBehavior behavior) {
        if (behavior == null) return new ForgeSpawnEggItem(type, background, highlight, properties);
        return new ForgeSpawnEggItem(type, background, highlight, properties) {
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
        return new RecordItem(comparatorOutput, sound, properties, lengthTicks);
    }
    @Override public boolean allowTreeGrowth(LevelAccessor level, RandomSource random, BlockPos pos) {
        return ForgeEventFactory.saplingGrowTree(level, random, pos);
    }
    @Override public boolean mobGriefing(Level level, Entity entity) { return ForgeEventFactory.getMobGriefingEvent(level, entity); }
    @Override public boolean restoringBlockSnapshots(Level level) { return level.restoringBlockSnapshots; }
    @Override public FoodProperties foodProperties(ItemStack stack, @Nullable LivingEntity entity) { return stack.getFoodProperties(entity); }
    @Override public CompoundTag persistentData(Entity entity) { return entity.getPersistentData(); }
    @Override public Network.Transport network() { return this; }

    @Override public Packet<?> toClient(ResourceLocation id, FriendlyByteBuf payload) {
        return channel.toVanillaPacket(new Payload(id, payload), NetworkDirection.PLAY_TO_CLIENT);
    }
    @Override public Packet<?> toServer(ResourceLocation id, FriendlyByteBuf payload) {
        return channel.toVanillaPacket(new Payload(id, payload), NetworkDirection.PLAY_TO_SERVER);
    }
    @Override public boolean canSend(ServerPlayer player, ResourceLocation id) {
        return channel.isRemotePresent(player.connection.connection);
    }

    private void receive(Payload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER) Network.receiveOnServer(payload.id(), ctx.getSender(), payload.data());
        else Network.receiveOnClient(payload.id(), payload.data());
        ctx.setPacketHandled(true);
    }

    private record Payload(ResourceLocation id, FriendlyByteBuf data) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(id);
            buffer.writeBytes(data, data.readerIndex(), data.readableBytes());
        }
        static Payload read(FriendlyByteBuf buffer) {
            return new Payload(buffer.readResourceLocation(), new FriendlyByteBuf(buffer.readBytes(buffer.readableBytes())));
        }
    }
}
