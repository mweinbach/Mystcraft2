package com.technovision.advancedgenetics.gametest;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierBlockEntity;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerBlockEntity;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerScreenHandler;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterBlockEntity;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorBlockEntity;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserBlockEntity;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorBlockEntity;
import com.technovision.advancedgenetics.common.item.DnaItem;
import com.technovision.advancedgenetics.common.item.PlasmidItem;
import com.technovision.advancedgenetics.common.item.SyringeItem;
import com.technovision.advancedgenetics.common.recipe.cellanalyzer.CellAnalyzerRecipe;
import com.technovision.advancedgenetics.registry.BlockRegistry;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import com.technovision.advancedgenetics.registry.RecipeRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.energy;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.genetics;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.items;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.player;

@GameTestHolder(AdvancedGenetics.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineGameTests {

    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final int RECIPE_COUNT = 39;

    @GameTest(template = TEMPLATE)
    public static void cellAnalyzerRecipesLoadAndSurviveNetworkSync(GameTestHelper helper) {
        List<CellAnalyzerRecipe> recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeRegistry.CELL_ANALYZER_TYPE.get());
        check(recipes.size() == RECIPE_COUNT, "Expected " + RECIPE_COUNT + " cell analyzer recipes, got " + recipes.size());
        for (CellAnalyzerRecipe recipe : recipes) {
            String input = ForgeRegistries.ITEMS.getKey(recipe.getInput().getItem()).getPath();
            String output = ForgeRegistries.ITEMS.getKey(recipe.getOutput().getItem()).getPath();
            check(input.endsWith("_matter") && output.equals(input.replace("_matter", "_cell")),
                    recipe.getId() + " maps " + input + " to " + output);

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            RecipeRegistry.CELL_ANALYZER_SERIALIZER.get().toNetwork(buffer, recipe);
            CellAnalyzerRecipe synced = RecipeRegistry.CELL_ANALYZER_SERIALIZER.get().fromNetwork(recipe.getId(), buffer);
            check(ItemStack.matches(synced.getInput(), recipe.getInput()) && ItemStack.matches(synced.getOutput(), recipe.getOutput()),
                    recipe.getId() + " changed during network sync");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void cellAnalyzerUsesConfiguredTimeEnergyAndFaces(GameTestHelper helper) {
        CellAnalyzerBlockEntity analyzer = machine(helper, BlockRegistry.CELL_ANALYZER.get(), CellAnalyzerBlockEntity.class, 0);
        IItemHandler top = items(analyzer, Direction.UP);
        IEnergyStorage energy = energy(analyzer, Direction.NORTH);
        check(ItemHandlerHelper.insertItem(top, new ItemStack(Items.DIRT), false).getCount() == 1, "Cell analyzer accepted dirt");
        check(ItemHandlerHelper.insertItem(top, matter(EntityType.ZOMBIE), false).isEmpty(), "Cell analyzer rejected zombie matter");
        check(top.extractItem(0, 1, true).isEmpty(), "Automation can extract the input slot");

        int ticks = Config.Common.cellAnalyzerTicksPerOperation.get();
        int expectedEnergy = energy.getMaxEnergyStored() - (ticks + 1) * Config.Common.cellAnalyzerEnergyPerTick.get();
        helper.startSequence()
                .thenExecuteAfter(ticks / 2, () -> check(analyzer.getStackInSlot(1).isEmpty() && analyzer.getProgress() > 0,
                        "Cell analyzer is not halfway through its operation"))
                .thenWaitUntil(() -> check(analyzer.getStackInSlot(1).is(cell(EntityType.ZOMBIE)), "No zombie cell yet"))
                .thenExecute(() -> {
                    check(analyzer.getStackInSlot(0).isEmpty(), "Zombie matter was not consumed");
                    check(energy.getEnergyStored() == expectedEnergy,
                            "Expected " + expectedEnergy + " energy left, found " + energy.getEnergyStored());
                    check(top.extractItem(1, 1, false).is(cell(EntityType.ZOMBIE)), "Automation cannot extract the output");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void machineMenuMovesOnlyValidItems(GameTestHelper helper) {
        CellAnalyzerBlockEntity analyzer = machine(helper, BlockRegistry.CELL_ANALYZER.get(), CellAnalyzerBlockEntity.class, 0);
        FakePlayer player = player(helper, "ag_menu");
        player.getInventory().setItem(9, matter(EntityType.COW));
        player.getInventory().setItem(10, new ItemStack(Items.DIRT));

        CellAnalyzerScreenHandler menu = (CellAnalyzerScreenHandler) analyzer.createMenu(0, player.getInventory(), player);
        check(menu != null && menu.stillValid(player), "Nearby player cannot use the machine");
        check(menu.slots.size() == 38, "Menu has " + menu.slots.size() + " slots");
        check(menu.getPropertyDelegate().get(1) == Config.Common.cellAnalyzerTicksPerOperation.get(), "Menu max progress is wrong");
        menu.quickMoveStack(player, 0);
        menu.quickMoveStack(player, 1);
        check(analyzer.getStackInSlot(0).is(ItemRegistry.MATTER.get(EntityType.COW).get()), "Shift-click did not insert organic matter");
        check(player.getInventory().getItem(10).is(Items.DIRT), "Shift-click moved dirt into the machine");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dnaExtractorExtractsAnEntityGene(GameTestHelper helper) {
        DnaExtractorBlockEntity extractor = machine(helper, BlockRegistry.DNA_EXTRACTOR.get(), DnaExtractorBlockEntity.class,
                Config.Common.dnaExtractorMaxOverclock.get());
        check(ItemHandlerHelper.insertItem(items(extractor, Direction.UP), cellStack(EntityType.ZOMBIE), false).isEmpty(),
                "DNA extractor rejected a zombie cell");
        helper.succeedWhen(() -> {
            ItemStack dna = extractor.getStackInSlot(1);
            check(dna.is(ItemRegistry.DNA_HELIX.get()), "No DNA helix yet");
            Genes gene = Genes.getGeneByItem(dna);
            check(gene != null && Set.of(Genes.BASIC, Genes.RESISTANCE).contains(gene), "Zombie DNA has gene " + dna.getTag());
            check(!DnaItem.isDecoded(dna), "Extracted DNA is already decoded");
            check(extractor.getStackInSlot(0).isEmpty(), "Zombie cell was not consumed");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dnaDecrypterDecodesAndStacksHelices(GameTestHelper helper) {
        DnaDecrypterBlockEntity decrypter = machine(helper, BlockRegistry.DNA_DECRYPTER.get(), DnaDecrypterBlockEntity.class,
                Config.Common.dnaDecrypterMaxOverclock.get());
        ItemStack encoded = dna(Genes.RESISTANCE, false);
        encoded.setCount(2);
        check(ItemHandlerHelper.insertItem(items(decrypter, Direction.UP), encoded, false).isEmpty(), "DNA decrypter rejected DNA");
        helper.succeedWhen(() -> {
            ItemStack decoded = decrypter.getStackInSlot(1);
            check(decoded.getCount() == 2, "Decoded " + decoded.getCount() + " of 2 helices");
            check(DnaItem.isDecoded(decoded) && Genes.getGeneByItem(decoded) == Genes.RESISTANCE, "Decoded DNA is wrong: " + decoded.getTag());
        });
    }

    @GameTest(template = TEMPLATE)
    public static void plasmidInfuserAddsDecodedDna(GameTestHelper helper) {
        check(!PlasmidItem.canCombine(dna(Genes.BASIC, true), new ItemStack(ItemRegistry.PLASMID.get())),
                "Basic DNA can start an empty plasmid");
        PlasmidInfuserBlockEntity infuser = machine(helper, BlockRegistry.PLASMID_INFUSER.get(), PlasmidInfuserBlockEntity.class,
                Config.Common.plasmidInfuserMaxOverclock.get());
        ItemStack decoded = dna(Genes.SPEED, true);
        decoded.setCount(2);
        check(ItemHandlerHelper.insertItem(items(infuser, Direction.UP), decoded, false).isEmpty(), "Plasmid infuser rejected DNA");
        infuser.setStackInSlot(1, new ItemStack(ItemRegistry.PLASMID.get()));
        helper.succeedWhen(() -> {
            ItemStack plasmid = infuser.getStackInSlot(1);
            check(Genes.getGeneByItem(plasmid) == Genes.SPEED, "Plasmid gene is " + plasmid.getTag());
            check(plasmid.getOrCreateTag().getInt("count") == 4, "Plasmid count is " + plasmid.getOrCreateTag().getInt("count"));
            check(infuser.getStackInSlot(0).isEmpty(), "DNA was not consumed");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void bloodPurifierPurifiesOnlyFilledSyringes(GameTestHelper helper) {
        BloodPurifierBlockEntity purifier = machine(helper, BlockRegistry.BLOOD_PURIFIER.get(), BloodPurifierBlockEntity.class,
                Config.Common.bloodPurifierMaxOverclock.get());
        purifier.setStackInSlot(0, new ItemStack(ItemRegistry.SYRINGE.get()));
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(purifier.getStackInSlot(1).isEmpty() && purifier.getProgress() == 0, "An empty syringe was processed");
                    ItemStack blood = new ItemStack(ItemRegistry.SYRINGE.get());
                    SyringeItem.fill(blood, player(helper, "ag_donor", Genes.SPEED));
                    purifier.setStackInSlot(0, blood);
                })
                .thenWaitUntil(() -> {
                    ItemStack purified = purifier.getStackInSlot(1);
                    check(SyringeItem.isFilled(purified) && SyringeItem.isPurified(purified), "No purified blood yet");
                    check(purified.getOrCreateTag().getCompound("genes").contains(Genes.SPEED.toString()), "Purified blood lost its genes");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void plasmidInjectorLoadsASyringeThatInjectsTheGene(GameTestHelper helper) {
        PlasmidInjectorBlockEntity injector = machine(helper, BlockRegistry.PLASMID_INJECTOR.get(), PlasmidInjectorBlockEntity.class,
                Config.Common.plasmidInjectorMaxOverclock.get());
        FakePlayer patient = player(helper, "ag_patient");
        ItemStack syringe = new ItemStack(ItemRegistry.SYRINGE.get());
        SyringeItem.fill(syringe, patient);
        SyringeItem.purify(syringe);

        ItemStack plasmid = new ItemStack(ItemRegistry.PLASMID.get());
        plasmid.getOrCreateTag().putString("gene", Genes.FLIGHT.toString());
        plasmid.getOrCreateTag().putInt("count", PlasmidItem.maxGenes());
        check(ItemHandlerHelper.insertItem(items(injector, Direction.UP), plasmid, false).isEmpty(), "Plasmid injector rejected a full plasmid");
        injector.setStackInSlot(1, syringe);

        helper.startSequence()
                .thenWaitUntil(() -> check(injector.getStackInSlot(0).isEmpty(), "Plasmid was not injected yet"))
                .thenExecute(() -> {
                    ItemStack loaded = injector.getStackInSlot(1);
                    CompoundTag genes = loaded.getOrCreateTag().getCompound("genes");
                    check(genes.contains(Genes.FLIGHT.toString()) && !genes.getBoolean(Genes.FLIGHT.toString()), "Syringe genes are " + genes);
                    check(!SyringeItem.isPurified(loaded), "Loading a plasmid left the syringe purified");

                    SyringeItem.purify(loaded);
                    Item syringeItem = ItemRegistry.SYRINGE.get();
                    FakePlayer stranger = player(helper, "ag_stranger");
                    syringeItem.finishUsingItem(loaded, helper.getLevel(), stranger);
                    check(!genetics(stranger).hasGene(Genes.FLIGHT), "Another player's blood was injected with gene sharing off");

                    syringeItem.finishUsingItem(loaded, helper.getLevel(), patient);
                    check(genetics(patient).hasGene(Genes.FLIGHT), "Injection did not grant Flight");
                    check(!SyringeItem.isFilled(loaded), "Syringe was not emptied after injection");
                })
                .thenSucceed();
    }

    private static <T extends AbstractInventoryBlockEntity> T machine(GameTestHelper helper, Block block, Class<T> type, int overclock) {
        helper.setBlock(MACHINE, block);
        BlockEntity blockEntity = helper.getBlockEntity(MACHINE);
        check(type.isInstance(blockEntity), "Expected " + type.getSimpleName() + " but found " + blockEntity);
        T machine = type.cast(blockEntity);
        machine.setOverclock(overclock);
        IEnergyStorage energy = energy(machine, Direction.DOWN);
        check(energy.receiveEnergy(energy.getMaxEnergyStored(), false) == energy.getMaxEnergyStored(), "Machine did not accept energy");
        return machine;
    }

    private static ItemStack matter(EntityType<?> type) {
        return new ItemStack(ItemRegistry.MATTER.get(type).get());
    }

    private static Item cell(EntityType<?> type) {
        return ItemRegistry.CELLS.get(type).get();
    }

    private static ItemStack cellStack(EntityType<?> type) {
        return new ItemStack(cell(type));
    }

    private static ItemStack dna(Genes gene, boolean decoded) {
        ItemStack stack = new ItemStack(ItemRegistry.DNA_HELIX.get());
        stack.getOrCreateTag().putString("gene", gene.toString());
        stack.getOrCreateTag().putBoolean("decoded", decoded);
        return stack;
    }
}
