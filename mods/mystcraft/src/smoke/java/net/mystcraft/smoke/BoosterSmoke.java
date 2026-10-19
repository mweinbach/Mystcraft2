package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.exploration.ArchivistShop;
import net.mystcraft.minecraft.exploration.ExplorationPages;
import net.mystcraft.minecraft.item.BoosterPackItem;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Actual item-use and merchant-result-slot transactions for the classic booster acquisition loop. */
public final class BoosterSmoke {
    private static final BlockPos POS = new BlockPos(100050, 80, 100050);
    private BoosterSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        Set<UUID> existingOrbs = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(POS).inflate(8)).stream()
                .map(Entity::getUUID).collect(Collectors.toSet());
        ServerPlayer player = player(server, level);
        MerchantMenu menu = null;
        Villager villager = null;
        try {
            ItemStack generated = BoosterPackItem.generate(level.registryAccess(), RandomSource.create(771));
            distribution(generated, level.registryAccess());
            require(ItemStack.matches(generated, BoosterPackItem.generate(level.registryAccess(), RandomSource.create(771))),
                    "seeded booster draws must be reproducible without source-stack aliasing");
            passed.add("boosters: sixteen implemented pages follow exact ordered7/4/4/1 rank draws and seeded generation is reproducible");

            ItemStack one = new ItemStack(MystcraftItems.BOOSTER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, one);
            player.gameMode.useItem(player, level, one, InteractionHand.MAIN_HAND);
            distribution(player.getItemInHand(InteractionHand.MAIN_HAND), level.registryAccess());
            require(count(player, MystcraftItems.BOOSTER.get()) == 0 && count(player, WritingDeskRegistry.FOLDER.get()) == 1,
                    "real single-pack use must replace the held pack with exactly one populated folder");

            player.getInventory().clearContent();
            ItemStack pair = new ItemStack(MystcraftItems.BOOSTER.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, pair);
            player.gameMode.useItem(player, level, pair, InteractionHand.MAIN_HAND);
            require(player.getItemInHand(InteractionHand.MAIN_HAND) == pair && pair.getCount() == 1
                    && count(player, WritingDeskRegistry.FOLDER.get()) == 1, "stacked booster use must consume one and add exactly one folder");
            for (ItemStack stack : player.getInventory().items) if (stack.is(WritingDeskRegistry.FOLDER.get())) distribution(stack, level.registryAccess());
            player.gameMode.useItem(player, level, pair, InteractionHand.MAIN_HAND);
            require(count(player, MystcraftItems.BOOSTER.get()) == 0 && count(player, WritingDeskRegistry.FOLDER.get()) == 2,
                    "using the remaining booster must produce exactly the second folder");
            passed.add("boosters: native ServerPlayerGameMode item use consumes single/stacked packs exactly once and delivers populated folders");

            player.getInventory().clearContent();
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            ItemStack fullPair = new ItemStack(MystcraftItems.BOOSTER.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, fullPair);
            player.gameMode.useItem(player, level, fullPair, InteractionHand.MAIN_HAND);
            require(player.getItemInHand(InteractionHand.MAIN_HAND) == fullPair && fullPair.getCount() == 2
                    && count(player, WritingDeskRegistry.FOLDER.get()) == 0, "full inventory must reject stacked packs without consumption or dropped output");
            ItemStack fullSingle = new ItemStack(MystcraftItems.BOOSTER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, fullSingle);
            player.gameMode.useItem(player, level, fullSingle, InteractionHand.MAIN_HAND);
            distribution(player.getItemInHand(InteractionHand.MAIN_HAND), level.registryAccess());
            require(count(player, WritingDeskRegistry.FOLDER.get()) == 1, "single pack must safely replace its own slot even with full inventory");
            passed.add("boosters: full-inventory stacked-pack failure preserves input; single pack still replaces its own slot safely");

            player.getInventory().clearContent();
            villager = new Villager(EntityType.VILLAGER, level); villager.setPos(100050.5, 80, 100050.5);
            villager.setVillagerData(villager.getVillagerData().setProfession(ExplorationRegistry.ARCHIVIST.get()));
            ArchivistShop.ensureOffers(villager);
            int index = -1;
            for (int i = 0; i < villager.getOffers().size(); i++) if (villager.getOffers().get(i).getResult().is(MystcraftItems.BOOSTER.get())) index = i;
            require(index >= 0, "archivist must expose the booster purchase");
            var offer = villager.getOffers().get(index);
            require(offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() == 20 && offer.getMaxUses() == 5,
                    "booster purchase must start at twenty emeralds and five stock");
            villager.setTradingPlayer(player);
            menu = new MerchantMenu(360, player.getInventory(), villager);
            player.containerMenu = menu; menu.setSelectionHint(index);
            for (int purchase = 1; purchase <= 5; purchase++) {
                menu.getSlot(0).set(new ItemStack(Items.EMERALD, 20));
                menu.setSelectionHint(index);
                require(menu.getSlot(2).getItem().is(MystcraftItems.BOOSTER.get()), "paid merchant input must expose one booster result");
                require(menu.quickMoveStack(player, 2).is(MystcraftItems.BOOSTER.get())
                        && menu.getSlot(0).getItem().isEmpty() && offer.getUses() == purchase
                        && count(player, MystcraftItems.BOOSTER.get()) == purchase,
                        "real merchant result extraction must deduct twenty emeralds, deliver one booster and consume one stock");
            }
            menu.getSlot(0).set(new ItemStack(Items.EMERALD, 20)); menu.setSelectionHint(index);
            require(offer.isOutOfStock() && menu.getSlot(2).getItem().isEmpty() && menu.quickMoveStack(player, 2).isEmpty()
                    && menu.getSlot(0).getItem().getCount() == 20 && count(player, MystcraftItems.BOOSTER.get()) == 5,
                    "sixth purchase must be unavailable without consuming emeralds or creating a booster");
            CompoundTag saved = villager.saveWithoutId(new CompoundTag());
            Villager reloaded = new Villager(EntityType.VILLAGER, level); reloaded.load(saved); ArchivistShop.ensureOffers(reloaded);
            require(reloaded.getOffers().stream().filter(trade -> trade.getResult().is(MystcraftItems.BOOSTER.get())).count() == 1
                    && reloaded.getOffers().stream().filter(trade -> trade.getResult().is(MystcraftItems.BOOSTER.get())).findFirst().orElseThrow().getUses() == 5,
                    "merchant reload must preserve exhausted booster stock without duplicating or refreshing the offer");
            reloaded.discard();
            passed.add("boosters: five actual merchant-menu purchases pay20 each; exhausted stock blocks sixth and survives merchant NBT reload");
            return List.copyOf(passed);
        } finally {
            if (menu != null) menu.removed(player);
            if (villager != null) villager.discard();
            player.containerMenu = player.inventoryMenu;
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(POS).inflate(8))) if (!existingOrbs.contains(orb.getUUID())) orb.discard();
        }
    }

    private static void distribution(ItemStack folder, net.minecraft.core.RegistryAccess registry) {
        require(folder.is(WritingDeskRegistry.FOLDER.get()), "opened booster must produce the registered folder item");
        var pages = PageCollectionItem.pages(folder); require(pages.size() == 16, "booster must contain all sixteen pages; unavailable rank pools cannot silently shorten a pack");
        for (int i = 0; i < pages.size(); i++) {
            ItemStack page = new ItemStack(net.mystcraft.integration.registry.AgeRegistry.PAGE.get());
            PageData.applyPayload(page, pages.get(i));
            int rank = ExplorationPages.rank(page, registry);
            require(rank >= 0, "booster must emit a ranked page from the current world's usable native pool");
            int expected = i < 7 ? 0 : i < 11 ? 1 : i < 15 ? 2 : 3;
            require(expected == 3 ? rank >= 3 : rank == expected, "booster page order/rank distribution differs from seven/four/four/one source draws");
        }
    }
    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "MystSmokeBooster"), null);
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        player.setPos(100050.5, 80, 100050.5); level.addNewPlayer(player);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return player;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
