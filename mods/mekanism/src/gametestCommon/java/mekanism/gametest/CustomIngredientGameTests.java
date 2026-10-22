/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import mekanism.additions.common.registries.AdditionsBlocks;
import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.api.recipes.NucleosynthesizingRecipe;
import mekanism.api.recipes.PaintingRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.text.EnumColor;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.ingredient.creator.ItemStackIngredientCreator.SingleItemStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismPigments;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Forge ingredient types used by Mekanism recipes, plus the other built-in types a datapack can declare. Both loaders
 * read them through {@link Ingredient#fromJson}.
 */
public class CustomIngredientGameTests {

    private static final long NUCLEOSYNTHESIZER_ENERGY = 30_000_000L;

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void paintingDifferenceAcceptsOtherGlass(GameTestHelper helper) {
        Ingredient ingredient = paintingGlass(helper);
        check(!ingredient.isVanilla(), "White glass painting input was read as a vanilla ingredient");
        check(ingredient.isSimple(), "A tag minus one item was not simple, so the recipe cache will not index it by item");
        check(ingredient.test(new ItemStack(Items.BLUE_STAINED_GLASS)), "Blue stained glass was excluded from the white painting recipe");
        check(!ingredient.test(new ItemStack(Items.WHITE_STAINED_GLASS)), "White stained glass matched the recipe that subtracts it");
        Ingredient read = roundTrip(ingredient);
        check(!read.isVanilla() && read.isSimple(), "The painting ingredient did not survive the network");
        check(read.test(new ItemStack(Items.BLUE_STAINED_GLASS)) && !read.test(new ItemStack(Items.WHITE_STAINED_GLASS)),
              "The networked painting ingredient matched a different set of glass");

        TileEntityMekanism painter = CoreMachineGameTests.poweredMachine(helper, MekanismBlocks.PAINTING_MACHINE);
        ItemStack blue = new ItemStack(Items.BLUE_STAINED_GLASS);
        check(painter.getInventorySlots(null).get(1).isItemValid(blue), "The painting machine rejected blue stained glass");
        painter.getInventorySlots(null).get(1).setStack(blue);
        fillPigment(painter, RelativeSide.BACK, EnumColor.WHITE, 256);
        helper.succeedWhen(() -> {
            ItemStack output = painter.getInventorySlots(null).get(2).getStack();
            check(painter.getInventorySlots(null).get(1).isEmpty() && output.is(Items.WHITE_STAINED_GLASS) && output.getCount() >= 1,
                  "Blue stained glass became " + output + " instead of white stained glass");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void paintingDifferenceLeavesSubtractedGlass(GameTestHelper helper) {
        TileEntityMekanism painter = CoreMachineGameTests.poweredMachine(helper, MekanismBlocks.PAINTING_MACHINE);
        ItemStack white = new ItemStack(Items.WHITE_STAINED_GLASS);
        if (!painter.getInventorySlots(null).get(1).isItemValid(white)) {
            helper.succeed();
            return;
        }
        painter.getInventorySlots(null).get(1).setStack(white);
        // The pigment tank only accepts a pigment that completes a recipe with the item already in the slot. Subtracted
        // glass has no such recipe, so a rejected insert is the machine refusing to paint it.
        long moved = TestPlatform.insertPigment(painter, side(painter, RelativeSide.BACK),
              MekanismPigments.PIGMENT_COLOR_LOOKUP.get(EnumColor.WHITE).get(), 256, true);
        if (moved == 0) {
            helper.succeed();
            return;
        }
        helper.runAtTickTime(300, () -> {
            ItemStack input = painter.getInventorySlots(null).get(1).getStack();
            ItemStack output = painter.getInventorySlots(null).get(2).getStack();
            check(input.is(Items.WHITE_STAINED_GLASS) && output.isEmpty(),
                  "Subtracted white glass was consumed into " + output);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void strictNbtBowCraftsCrossbow(GameTestHelper helper) {
        Ingredient ingredient = nucleosynthesizerInput(helper, "nucleosynthesizing/crossbow");
        check(!ingredient.isVanilla() && !ingredient.isSimple(), "The crossbow input was not an exact NBT ingredient");
        ItemStack shown = ingredient.getItems()[0].copy();
        check(ingredient.test(shown), "The ingredient rejected its own stack " + shown.getTag());
        ItemStack damaged = shown.copy();
        damaged.setDamageValue(shown.getDamageValue() + 1);
        check(!ingredient.test(damaged), "A damaged bow matched " + shown.getTag());
        // Both loaders write Damage:0 onto a new damageable stack, so an undamaged bow matches {Damage:0}.
        ItemStack fresh = new ItemStack(Items.BOW);
        check(fresh.getDamageValue() == shown.getDamageValue() && ingredient.test(fresh),
              "An undamaged bow " + fresh.getTag() + " did not match " + shown.getTag());
        Ingredient read = roundTrip(ingredient);
        check(read.test(shown) && !read.test(damaged), "The networked bow ingredient did not keep its tag " + shown.getTag());

        Ingredient trident = nucleosynthesizerInput(helper, "nucleosynthesizing/trident");
        ItemStack sword = trident.getItems()[0].copy();
        check(sword.is(Items.DIAMOND_SWORD) && trident.test(sword), "The trident input is " + sword + " tag " + sword.getTag());

        TileEntityMekanism synthesizer = CoreMachineGameTests.poweredMachine(helper, MekanismBlocks.ANTIPROTONIC_NUCLEOSYNTHESIZER);
        FloatingLong leftover = ((IStrictEnergyHandler) synthesizer).insertEnergy(FloatingLong.createConst(NUCLEOSYNTHESIZER_ENERGY), Action.EXECUTE);
        check(leftover.isZero(), "The nucleosynthesizer left " + leftover + " J unstored");
        ItemStack input = shown.copy();
        check(synthesizer.getInventorySlots(null).get(1).isItemValid(input), "The nucleosynthesizer rejected " + input + " tag " + input.getTag());
        synthesizer.getInventorySlots(null).get(1).setStack(input);
        long moved = TestPlatform.insertBlockGas(synthesizer, side(synthesizer, RelativeSide.BACK), MekanismGases.ANTIMATTER.get(), 1, true);
        check(moved == 1, "Moved " + moved + " antimatter into the nucleosynthesizer");
        helper.succeedWhen(() -> {
            ItemStack output = synthesizer.getInventorySlots(null).get(2).getStack();
            check(synthesizer.getInventorySlots(null).get(1).isEmpty() && output.is(Items.CROSSBOW),
                  "The tagged bow became " + output + " instead of a crossbow");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void additionsRecolorsTransparentPlastic(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation("mekanismadditions", "plastic/transparent/recolor/black");
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        check(recipe instanceof ShapedRecipe, "Missing transparent plastic recolor recipe");
        boolean sawDifference = false;
        for (Ingredient ingredient : ((ShapedRecipe) recipe).getIngredients()) {
            if (!ingredient.isVanilla()) {
                sawDifference = true;
                check(ingredient.isSimple(), "The plastic difference ingredient was not simple");
                Item white = AdditionsBlocks.TRANSPARENT_PLASTIC_BLOCKS.get(EnumColor.WHITE).asItem();
                Item black = AdditionsBlocks.TRANSPARENT_PLASTIC_BLOCKS.get(EnumColor.BLACK).asItem();
                check(ingredient.test(new ItemStack(white)) && !ingredient.test(new ItemStack(black)),
                      "The plastic difference did not exclude black transparent plastic");
            }
        }
        check(sawDifference, "The recolor recipe had no custom ingredient");

        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        Item white = AdditionsBlocks.TRANSPARENT_PLASTIC_BLOCKS.get(EnumColor.WHITE).asItem();
        Item black = AdditionsBlocks.TRANSPARENT_PLASTIC_BLOCKS.get(EnumColor.BLACK).asItem();
        ItemStack dye = new ItemStack(Items.BLACK_DYE);
        ItemStack crafted = craft(helper, player, new BlockPos(1, 1, 1),
              plastic(white), plastic(white), plastic(white),
              plastic(white), dye, plastic(white),
              plastic(white), plastic(white), plastic(white));
        check(crafted.is(black) && crafted.getCount() == 8, "Recolor crafted " + crafted.getCount() + " " + crafted.getItem());
        ItemStack rejected = craft(helper, player, new BlockPos(3, 1, 1),
              plastic(black), plastic(black), plastic(black),
              plastic(black), dye.copy(), plastic(black),
              plastic(black), plastic(black), plastic(black));
        check(rejected.isEmpty(), "Black plastic recolored itself into " + rejected);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void otherForgeIngredientTypes(GameTestHelper helper) {
        Ingredient vanilla = Ingredient.fromJson(JsonParser.parseString("{\"item\":\"minecraft:stick\"}"));
        check(vanilla.isVanilla() && vanilla.test(new ItemStack(Items.STICK)), "A plain item ingredient was not left to vanilla");

        CompoundTag repair = new CompoundTag();
        repair.putInt("RepairCost", 1);
        Ingredient partial = Ingredient.fromJson(JsonParser.parseString(
              "{\"type\":\"forge:partial_nbt\",\"item\":\"minecraft:bow\",\"nbt\":\"{RepairCost:1}\"}"));
        check(!partial.isVanilla() && !partial.isSimple(), "Partial NBT was read as a simple ingredient");
        ItemStack repaired = new ItemStack(Items.BOW);
        repaired.setTag(repair.copy());
        ItemStack repairedAndDamaged = repaired.copy();
        repairedAndDamaged.getOrCreateTag().putInt("Damage", 3);
        check(partial.test(repaired) && partial.test(repairedAndDamaged), "Partial NBT rejected a bow that contains RepairCost");
        check(!partial.test(new ItemStack(Items.BOW)) && !partial.test(new ItemStack(Items.STICK)), "Partial NBT matched a bow without RepairCost");
        check(roundTrip(partial).test(repairedAndDamaged) && !roundTrip(partial).test(new ItemStack(Items.BOW)),
              "Partial NBT did not round-trip");

        Ingredient intersection = Ingredient.fromJson(JsonParser.parseString(
              "{\"type\":\"forge:intersection\",\"children\":[{\"item\":\"minecraft:stick\"},{\"item\":\"minecraft:stick\"}]}"));
        check(!intersection.isVanilla() && intersection.test(new ItemStack(Items.STICK)) && !intersection.test(new ItemStack(Items.STONE)),
              "An intersection of sticks matched something else");
        check(roundTrip(intersection).test(new ItemStack(Items.STICK)), "The intersection did not round-trip");

        Ingredient mixed = Ingredient.fromJson(JsonParser.parseString("["
              + "{\"type\":\"forge:difference\",\"base\":{\"item\":\"minecraft:stick\"},\"subtracted\":{\"item\":\"minecraft:apple\"}},"
              + "{\"item\":\"minecraft:stone\"}]"));
        check(!mixed.isVanilla() && mixed.isSimple(), "A mixed ingredient array was not a simple compound");
        check(mixed.test(new ItemStack(Items.STICK)) && mixed.test(new ItemStack(Items.STONE)) && !mixed.test(new ItemStack(Items.APPLE)),
              "The mixed array matched the wrong items");
        Ingredient mixedRead = roundTrip(mixed);
        check(mixedRead.test(new ItemStack(Items.STICK)) && mixedRead.test(new ItemStack(Items.STONE)) && !mixedRead.test(new ItemStack(Items.APPLE)),
              "The mixed array did not round-trip");

        expectSyntax("{\"type\":\"mekanism:nope\",\"item\":\"minecraft:stick\"}", "mekanism:nope");
        expectSyntax("{\"type\":\"forge:compound\",\"children\":[{\"item\":\"minecraft:stick\"}]}", "CompoundIngredient");
        helper.succeed();
    }

    private static void expectSyntax(String json, String messagePart) {
        try {
            Ingredient.fromJson(JsonParser.parseString(json));
            check(false, "Accepted ingredient " + json);
        } catch (JsonSyntaxException e) {
            check(e.getMessage() != null && e.getMessage().contains(messagePart), "Rejected " + json + " with " + e.getMessage());
        }
    }

    private static Ingredient paintingGlass(GameTestHelper helper) {
        Recipe<?> recipe = MekanismRecipeType.byKey(helper.getLevel(), new ResourceLocation("mekanism", "painting/glass/white")).orElse(null);
        check(recipe instanceof PaintingRecipe, "Missing white glass painting recipe");
        ItemStackIngredient input = ((PaintingRecipe) recipe).getItemInput();
        check(input instanceof SingleItemStackIngredient, "Painting input was " + input.getClass().getName());
        return ((SingleItemStackIngredient) input).getInputRaw();
    }

    private static Ingredient nucleosynthesizerInput(GameTestHelper helper, String path) {
        Recipe<?> recipe = MekanismRecipeType.byKey(helper.getLevel(), new ResourceLocation("mekanism", path)).orElse(null);
        check(recipe instanceof NucleosynthesizingRecipe, "Missing recipe " + path);
        ItemStackIngredient input = ((NucleosynthesizingRecipe) recipe).getItemInput();
        check(input instanceof SingleItemStackIngredient, path + " input was " + input.getClass().getName());
        return ((SingleItemStackIngredient) input).getInputRaw();
    }

    private static Ingredient roundTrip(Ingredient ingredient) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ingredient.toNetwork(buffer);
        Ingredient read = Ingredient.fromNetwork(buffer);
        buffer.release();
        return read;
    }

    private static void fillPigment(TileEntityMekanism machine, RelativeSide relative, EnumColor color, long amount) {
        long moved = TestPlatform.insertPigment(machine, side(machine, relative), MekanismPigments.PIGMENT_COLOR_LOOKUP.get(color).get(), amount, true);
        check(moved == amount, "Moved " + moved + " of " + amount + " " + color + " pigment");
    }

    private static Direction side(TileEntityMekanism tile, RelativeSide relative) {
        return relative.getDirection(tile.getDirection());
    }

    private static ItemStack plastic(Item item) {
        return new ItemStack(item);
    }

    private static ItemStack craft(GameTestHelper helper, ServerPlayer player, BlockPos table, ItemStack... grid) {
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        for (int i = 0; i < grid.length; i++) {
            menu.getSlot(i + 1).set(grid[i]);
        }
        return menu.getSlot(0).getItem().copy();
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "ingredient-test"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }
}
