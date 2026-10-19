package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.minecraft.ink.InkAdditives;
import net.mystcraft.minecraft.ink.InkMixerBlockEntity;
import net.mystcraft.minecraft.ink.InkMixerMenu;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.mystcraft.minecraft.writing.PageData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server runtime checks for the source-derived modifier parser and additive math/transactions. */
public final class LinkModifierSmoke {
    private LinkModifierSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        ItemStack book = new ItemStack(MystcraftItems.LINKING_BOOK.get());
        require(!LinkModifiers.from(book).allowsDimension(true) && LinkModifiers.from(book).allowsDimension(false), "Default dimension restrictions changed");
        PageData.addLinkProperty(book, LinkModifiers.INTRA_LINKING);
        require(LinkModifiers.from(book).allowsDimension(true), "Panel intra linking flag not read");
        CompoundTag firstPage = new CompoundTag();
        PageData.makeLinkPanel(firstPage);
        var panel = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        panel.setTag(firstPage);
        PageData.addLinkProperty(panel, LinkModifiers.INTRA_LINKING_ONLY);
        PageData.addLinkProperty(panel, LinkModifiers.MAINTAIN_MOMENTUM);
        PageData.writePages(book.getOrCreateTag(), List.of(PageData.payload(panel)));
        var parsed = LinkModifiers.from(book);
        require(parsed.intraLinkingOnly() && parsed.maintainMomentum() && parsed.allowsDimension(true) && !parsed.allowsDimension(false), "First page flags or intra-only restrictions wrong");
        CompoundTag flags = new CompoundTag();
        flags.putBoolean(LinkModifiers.FOLLOWING, true);
        flags.putBoolean(LinkModifiers.DISARM, true);
        flags.putBoolean(LinkModifiers.INTRA_LINKING_ONLY, false);
        book.getOrCreateTag().put("Flags", flags);
        CompoundTag original = book.getTag().copy();
        parsed = LinkModifiers.from(book);
        require(parsed.following() && parsed.disarm() && parsed.allowsDimension(false), "Legacy root flags or explicit false override not read");
        require(original.equals(book.getTag()), "Modifier parsing mutated book NBT");
        passed.add("link modifiers: default/intra/intra-only restrictions, panel and first-page properties, legacy Flags compound and false overrides parse without mutation");

        Map<String, Float> ink = new HashMap<>();
        InkAdditives.mix(ink, InkAdditives.effects(new ItemStack(Items.GUNPOWDER)));
        InkAdditives.mix(ink, InkAdditives.effects(new ItemStack(Items.ENDER_PEARL)));
        close(ink.get(LinkModifiers.DISARM), .29F, "Source disarm mixing math");
        close(ink.get(LinkModifiers.INTRA_LINKING), .15F, "Source intra mixing math");
        Map<String, Float> beforeBlack = Map.copyOf(ink);
        InkAdditives.mix(ink, InkAdditives.effects(new ItemStack(Items.BLACK_DYE)));
        require(beforeBlack.equals(ink), "Empty-name black dye mapping diverged from legacy tile implementation");
        ItemStack rolled = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        ItemStack again = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        Map<String, Float> certain = Map.of(LinkModifiers.DISARM, 1F, LinkModifiers.MAINTAIN_MOMENTUM, 1F);
        long next = InkAdditives.apply(rolled, certain, 12345L);
        require(next == InkAdditives.apply(again, certain, 12345L) && ItemStack.matches(rolled, again), "Same ink seed rolled different panel NBT");
        require(PageData.linkProperties(rolled).containsAll(certain.keySet()), "Independent successful properties failed to coexist");
        passed.add("ink additives: exact inverse-total mixing recurrence, legacy empty-name dye behavior, deterministic seeded independent panel properties");

        BlockPos pos = new BlockPos(100040, 80, 100040);
        BlockState previous = level.getBlockState(pos);
        BlockEntity old = level.getBlockEntity(pos);
        CompoundTag oldTag = old == null ? null : old.saveWithFullMetadata();
        try {
            BlockState state = InkMixerRegistry.INK_MIXER.get().defaultBlockState();
            level.setBlock(pos, state, 3);
            InkMixerBlockEntity mixer = (InkMixerBlockEntity) level.getBlockEntity(pos);
            require(mixer != null, "Modifier test mixer missing");
            mixer.inventory.setItem(0, new ItemStack(InkMixerRegistry.INK_VIAL.get()));
            InkMixerBlockEntity.tick(level, pos, state, mixer);
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.fromString("893633f4-49cc-4923-8528-5fe23f96ceaa"), "InkModifierSmoke"), null);
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            AbstractContainerMenu menu = new InkMixerMenu(1, player.getInventory(), mixer.inventory, mixer.data, pos);
            menu.setCarried(new ItemStack(Items.GUNPOWDER, 3));
            require(menu.clickMenuButton(player, 1) && menu.getCarried().getCount() == 2, "Right basin click failed exact one-item consumption");
            close(mixer.probabilities().get(LinkModifiers.DISARM), .2F, "Right click probability");
            require(menu.clickMenuButton(player, 0) && menu.getCarried().isEmpty(), "Left basin click failed stack consumption");
            close(mixer.probabilities().get(LinkModifiers.DISARM), .488F, "Three gunpowder recurrence");
            CompoundTag saved = ((BlockEntity) mixer).saveWithFullMetadata();
            InkMixerBlockEntity restored = new InkMixerBlockEntity(pos, state);
            ((BlockEntity) restored).load(saved);
            require(restored.probabilities().equals(mixer.probabilities()) && ((BlockEntity) restored).saveWithFullMetadata().getLong("NextSeed") == saved.getLong("NextSeed"), "Ink probabilities or RNG seed failed persistence");
            player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            menu.setCarried(new ItemStack(Items.FEATHER));
            require(!menu.clickMenuButton(player, 0) && menu.getCarried().getCount() == 1, "Distant basin mutation consumed additive");
            mixer.inventory.setItem(1, new ItemStack(Items.PAPER));
            InkMixerBlockEntity.tick(level, pos, state, mixer);
            require(mixer.inventory.getItem(1).getCount() == 1 && mixer.data.get(0) == 1 && mixer.inventory.getItem(3).isEmpty(), "Paper insertion materialized a virtual modified panel");
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            menu.setCarried(new ItemStack(Items.FEATHER));
            require(menu.clickMenuButton(player, 1), "Paper already present prevented basin additive");
            menu.broadcastChanges();
            require(menu.getSlot(3).remove(1).is(MystcraftItems.LINKING_PANEL.get()), "Modified result could not be extracted");
            require(mixer.inventory.getItem(0).isEmpty() && mixer.inventory.getItem(1).isEmpty() && mixer.inventory.getItem(2).getCount() == 1 && mixer.inventory.getItem(3).isEmpty(), "Modified panel changed base vial/paper/bottle costs");
            require(mixer.probabilities().isEmpty() && mixer.data.get(0) == 0, "Crafted panel left reusable additive probabilities");
            passed.add("ink basin: server-scoped left/right clicks consume correct additive counts, probabilities and seed persist, distance blocks mutation, and modified panels retain exact base costs and clear the mixture");
        } finally {
            if (level.getBlockEntity(pos) instanceof InkMixerBlockEntity mixer) mixer.inventory.clearContent();
            level.setBlock(pos, previous, 3);
            if (oldTag != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(oldTag);
        }
        return passed;
    }
    private static void close(float actual, float expected, String message) { require(Math.abs(actual - expected) < .00001F, message + ": " + actual); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
