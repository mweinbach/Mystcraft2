package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.minecraft.ink.InkMixerBlockEntity;
import net.mystcraft.minecraft.ink.InkMixerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime-only assertions using the loaded server registries, recipes and real menu inventories. */
public final class RecipeAndMixerSmoke {
    private RecipeAndMixerSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        recipes(level, passed);
        BlockPos pos = new BlockPos(100010, 80, 100010);
        BlockState previous = level.getBlockState(pos);
        BlockEntity previousEntity = level.getBlockEntity(pos);
        CompoundTag previousData = previousEntity == null ? null : previousEntity.saveWithFullMetadata();
        try {
            BlockState state = InkMixerRegistry.INK_MIXER.get().defaultBlockState();
            require(level.setBlock(pos, state, 3), "Could not place runtime mixer");
            require(level.getBlockEntity(pos) instanceof InkMixerBlockEntity, "Placed mixer has no block entity");
            InkMixerBlockEntity mixer = (InkMixerBlockEntity) level.getBlockEntity(pos);
            mixer.inventory.setItem(0, new ItemStack(InkMixerRegistry.INK_VIAL.get(), 3));
            mixer.inventory.setItem(1, new ItemStack(Items.PAPER, 3));
            for (int i = 0; i < 8; i++) tick(level, pos, state, mixer);
            count(mixer, 0, InkMixerRegistry.INK_VIAL.get(), 2);
            count(mixer, 1, Items.PAPER, 3);
            count(mixer, 2, Items.GLASS_BOTTLE, 1);
            require(mixer.inventory.getItem(3).isEmpty() && mixer.data.get(0) == 1, "Idle ticks materialized an unpaid preview");
            CompoundTag beforePreview = ((BlockEntity) mixer).saveWithFullMetadata();
            for (int i = 0; i < 5; i++) require(mixer.preview().is(MystcraftItems.LINKING_PANEL.get()), "Expected virtual panel preview");
            require(beforePreview.equals(((BlockEntity) mixer).saveWithFullMetadata()), "Preview mutated inventory or random seed");
            int extracted = 0;
            for (int i = 0; i < 3; i++) {
                tick(level, pos, state, mixer);
                ItemStack taken = mixer.takePanel(1);
                require(taken.is(MystcraftItems.LINKING_PANEL.get()), "Paid panel could not be extracted");
                extracted += taken.getCount();
            }
            for (int i = 0; i < 8; i++) tick(level, pos, state, mixer);
            require(extracted == 3 && mixer.takePanel(1).isEmpty(), "Three batches duplicated or lost extracted panels");
            count(mixer, 0, InkMixerRegistry.INK_VIAL.get(), 0);
            count(mixer, 1, Items.PAPER, 0);
            count(mixer, 2, Items.GLASS_BOTTLE, 3);
            count(mixer, 3, MystcraftItems.LINKING_PANEL.get(), 0);
            require(mixer.data.get(0) == 0, "Consumed batches left free ink");
            passed.add("mixer: previews and idle ticks consume no paper or random seed; three extractions cost exactly three vials/paper and return three panels/bottles");

            reset(mixer);
            mixer.inventory.setItem(0, new ItemStack(InkMixerRegistry.INK_VIAL.get(), 2));
            mixer.inventory.setItem(1, new ItemStack(Items.PAPER, 2));
            mixer.inventory.setItem(2, new ItemStack(Items.GLASS_BOTTLE, 64));
            tick(level, pos, state, mixer);
            count(mixer, 0, InkMixerRegistry.INK_VIAL.get(), 2);
            count(mixer, 1, Items.PAPER, 2);
            count(mixer, 2, Items.GLASS_BOTTLE, 64);
            require(mixer.inventory.getItem(3).isEmpty(), "Full bottle output allowed crafting");
            require(mixer.data.get(0) == 0, "Full bottle output consumed ink vial");

            mixer.inventory.setItem(2, ItemStack.EMPTY);
            mixer.inventory.setItem(3, new ItemStack(MystcraftItems.LINKING_PANEL.get(), 64));
            for (int i = 0; i < 4; i++) tick(level, pos, state, mixer);
            count(mixer, 0, InkMixerRegistry.INK_VIAL.get(), 1);
            count(mixer, 1, Items.PAPER, 2);
            count(mixer, 2, Items.GLASS_BOTTLE, 1);
            count(mixer, 3, MystcraftItems.LINKING_PANEL.get(), 64);
            require(mixer.data.get(0) == 1, "Full panel output lost its paid basin charge");
            passed.add("mixer: full bottle output preserves inputs; full panel output preserves paper and holds only one paid ink charge");

            CompoundTag saved = ((BlockEntity) mixer).saveWithFullMetadata();
            InkMixerBlockEntity loaded = new InkMixerBlockEntity(pos, state);
            ((BlockEntity) loaded).load(saved);
            require(loaded.data.get(0) == 1, "Ink charge did not survive NBT round trip");
            for (int i = 0; i < 4; i++) require(ItemStack.matches(mixer.inventory.getItem(i), loaded.inventory.getItem(i)), "Inventory NBT mismatch at slot " + i);
            ((BlockEntity) loaded).setLevel(level);
            require(loaded.takePanel(1).getCount() == 1, "Paid legacy output could not be recovered");
            count(loaded, 1, Items.PAPER, 2);
            require(loaded.data.get(0) == 1, "Recovery output was charged twice");
            loaded.inventory.setItem(3, ItemStack.EMPTY);
            require(loaded.takePanel(1).is(MystcraftItems.LINKING_PANEL.get()), "Restored ink failed extraction");
            count(loaded, 0, InkMixerRegistry.INK_VIAL.get(), 1);
            count(loaded, 1, Items.PAPER, 1);
            count(loaded, 2, Items.GLASS_BOTTLE, 1);
            count(loaded, 3, MystcraftItems.LINKING_PANEL.get(), 0);
            require(loaded.data.get(0) == 0, "Restored charge was not consumed once");
            passed.add("mixer: ink and all four inventories survive NBT round trip and restored ink pays for exactly one panel");

            menuChecks(server, level, pos, mixer, passed);
        } finally {
            if (level.getBlockEntity(pos) instanceof InkMixerBlockEntity mixer) reset(mixer);
            level.setBlock(pos, previous, 3);
            if (previousData != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(previousData);
        }
        return passed;
    }

    private static void recipes(ServerLevel level, List<String> passed) {
        CraftingContainer grid = grid();
        grid.setItem(0, new ItemStack(Items.INK_SAC));
        grid.setItem(1, new ItemStack(Items.BLACK_DYE));
        grid.setItem(2, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        CraftingRecipe water = matching(level, grid, InkMixerRegistry.INK_VIAL.get());
        require(water.assemble(grid).getCount() == 1, "Bottle ink recipe output count");
        require(water.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty), "Water bottle recipe left an extra container");
        grid.setItem(2, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD));
        require(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).isEmpty(), "Nonwater potion accepted as ink water");
        grid.setItem(2, new ItemStack(Items.WATER_BUCKET));
        grid.setItem(3, new ItemStack(Items.GLASS_BOTTLE));
        CraftingRecipe bucket = matching(level, grid, InkMixerRegistry.INK_VIAL.get());
        require(bucket.getRemainingItems(grid).get(2).is(Items.BUCKET), "Water bucket recipe failed to return bucket");
        grid.setItem(1, ItemStack.EMPTY);
        require(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).isEmpty(), "Ink vial recipe accepted only one black dye");
        passed.add("recipes: both ink vial variants match loaded RecipeManager, require two dyes and water, and return the bucket");

        grid.clearContent();
        ItemStack panel = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        panel.getOrCreateTag().putString("SmokePanelMarker", "retained");
        CompoundTag property = new CompoundTag();
        property.putBoolean("IntraLinking", true);
        panel.getOrCreateTag().put("SmokeNestedProperties", property);
        grid.setItem(0, panel);
        grid.setItem(1, new ItemStack(Items.LEATHER));
        ItemStack book = matching(level, grid, MystcraftItems.UNLINKED_LINK_BOOK.get()).assemble(grid);
        require(panel.getTag().equals(book.getTag()), "Panel data lost during leather-cover crafting");
        book.getOrCreateTag().putString("SmokePanelMarker", "changed");
        require(panel.getTag().getString("SmokePanelMarker").equals("retained"), "Book recipe aliased original panel NBT");
        passed.add("recipes: leather plus panel creates unlinked book and copies nested panel NBT without aliasing");

        grid.clearContent();
        grid.setItem(0, new ItemStack(Items.STICK));
        grid.setItem(2, new ItemStack(Items.STICK));
        grid.setItem(4, new ItemStack(Items.OAK_PLANKS));
        require(matching(level, grid, MystcraftItems.BOOKSTAND.get()).assemble(grid).getCount() == 1, "Bookstand recipe output count");
        passed.add("recipes: classic two sticks plus planks bookstand recipe resolves common tags");
    }

    private static void menuChecks(MinecraftServer server, ServerLevel level, BlockPos pos, InkMixerBlockEntity mixer, List<String> passed) {
        reset(mixer);
        ServerPlayer first = new ServerPlayer(server, level, new GameProfile(UUID.fromString("1f5ecc42-6ddc-4448-aef5-83702e4b8771"), "MixerSmokeOne"), null);
        ServerPlayer second = new ServerPlayer(server, level, new GameProfile(UUID.fromString("1f5ecc42-6ddc-4448-aef5-83702e4b8772"), "MixerSmokeTwo"), null);
        first.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        second.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        first.getInventory().clearContent();
        second.getInventory().clearContent();
        AbstractContainerMenu one = new InkMixerMenu(1, first.getInventory(), mixer.inventory, mixer.data, pos);
        AbstractContainerMenu two = new InkMixerMenu(2, second.getInventory(), mixer.inventory, mixer.data, pos);
        require(one.stillValid(first) && two.stillValid(second), "Nearby players cannot access mixer menu");
        require(!one.getSlot(2).mayPlace(new ItemStack(Items.GLASS_BOTTLE)), "Bottle output accepts insertion");
        require(!one.getSlot(3).mayPlace(new ItemStack(MystcraftItems.LINKING_PANEL.get())), "Panel output accepts insertion");
        mixer.inventory.setItem(3, new ItemStack(MystcraftItems.LINKING_PANEL.get(), 5));
        one.quickMoveStack(first, 3);
        require(two.quickMoveStack(second, 3).isEmpty(), "Second viewer extracted already taken result");
        require(total(first, MystcraftItems.LINKING_PANEL.get()) == 5 && total(second, MystcraftItems.LINKING_PANEL.get()) == 0, "Shared result duplicated or lost");
        require(mixer.inventory.getItem(3).isEmpty(), "Taken result remains in mixer");
        first.getInventory().clearContent();
        for (int i = 0; i < 36; i++) first.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        mixer.inventory.setItem(3, new ItemStack(MystcraftItems.LINKING_PANEL.get(), 5));
        require(one.quickMoveStack(first, 3).isEmpty(), "Full inventory accepted output");
        count(mixer, 3, MystcraftItems.LINKING_PANEL.get(), 5);
        reset(mixer);
        mixer.inventory.setItem(0, new ItemStack(InkMixerRegistry.INK_VIAL.get()));
        mixer.inventory.setItem(1, new ItemStack(Items.PAPER, 2));
        tick(level, pos, ((BlockEntity) mixer).getBlockState(), mixer);
        one.broadcastChanges(); two.broadcastChanges();
        require(one.getSlot(3).hasItem() && two.getSlot(3).hasItem() && mixer.inventory.getItem(3).isEmpty(), "Shared virtual preview absent or materialized");
        require(one.quickMoveStack(first, 3).isEmpty(), "Full inventory accepted virtual output");
        require(mixer.data.get(0) == 1 && mixer.inventory.getItem(1).getCount() == 2, "Failed preview transfer consumed ink or paper");
        first.getInventory().clearContent();
        require(!one.quickMoveStack(first, 3).isEmpty() && two.quickMoveStack(second, 3).isEmpty(), "Two viewers duplicated a virtual result");
        require(total(first, MystcraftItems.LINKING_PANEL.get()) == 1 && mixer.inventory.getItem(1).getCount() == 1 && mixer.data.get(0) == 0, "Virtual result extraction charged wrong amount");
        first.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        require(!one.stillValid(first), "Distant player can still use mixer");
        passed.add("menus: shared virtual and paid outputs cannot duplicate, full inventories consume nothing, output slots reject insertion, and distance closes access");
    }

    private static int total(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
    private static CraftingContainer grid() {
        return new CraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        }, 3, 3);
    }
    private static CraftingRecipe matching(ServerLevel level, CraftingContainer grid, Item output) {
        CraftingRecipe recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow(() -> new AssertionError("No loaded crafting recipe for " + output));
        require(recipe.assemble(grid).is(output), "Recipe matched wrong output: " + recipe.getId());
        return recipe;
    }
    private static void tick(ServerLevel level, BlockPos pos, BlockState state, InkMixerBlockEntity mixer) { InkMixerBlockEntity.tick(level, pos, state, mixer); }
    private static void reset(InkMixerBlockEntity mixer) { mixer.inventory.clearContent(); mixer.data.set(0, 0); }
    private static void count(InkMixerBlockEntity mixer, int slot, Item item, int expected) {
        ItemStack stack = mixer.inventory.getItem(slot);
        require(expected == 0 ? stack.isEmpty() : stack.is(item) && stack.getCount() == expected, "Mixer slot " + slot + " expected " + expected + " of " + item + ", got " + stack);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
