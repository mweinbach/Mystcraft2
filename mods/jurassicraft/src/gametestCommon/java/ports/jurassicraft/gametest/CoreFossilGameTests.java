package ports.jurassicraft.gametest;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/** Checks the registered fossil content in a running Fabric world. */
public final class CoreFossilGameTests {
    public CoreFossilGameTests() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation(PortMod.MOD_ID, path);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "jurassicraft:empty")
    public static void fossilCatalogIsRegistered(GameTestHelper helper) {
        check(helper, Registry.ITEM.getOptional(id("amber")).orElse(null) == JurassicContent.AMBER.get(),
                "amber must be registered under its stable id");
        check(helper, JurassicContent.TAB.makeIcon().is(JurassicContent.AMBER.get()),
                "the creative tab must use the registered amber item");
        check(helper, JurassicContent.FOSSILS.size() == Species.values().length
                        && JurassicContent.ENCASED_FOSSILS.size() == Species.values().length
                        && JurassicContent.BONES.size() == Species.values().length,
                "every species must have a fossil, an encased fossil and a bone catalog");

        for (Species species : Species.values()) {
            Block fossil = JurassicContent.FOSSILS.get(species).get();
            Block encased = JurassicContent.ENCASED_FOSSILS.get(species).get();
            String fossilId = species.id() + "_fossil_block";
            String encasedId = species.id() + "_encased_fossil";
            checkBlockAndItem(helper, fossilId, fossil);
            checkBlockAndItem(helper, encasedId, encased);
            var bones = JurassicContent.BONES.get(species);
            check(helper, bones.keySet().equals(Set.copyOf(species.bones())),
                    species.id() + " must have exactly its declared fossil bones");
            for (String bone : species.bones()) {
                var item = bones.get(bone).get();
                String boneId = species.id() + "_fossil_" + bone;
                check(helper, Registry.ITEM.getOptional(id(boneId)).orElse(null) == item,
                        "missing or incorrect bone item " + boneId);
            }
        }
        helper.succeed();
    }

    private static void checkBlockAndItem(GameTestHelper helper, String name, Block block) {
        check(helper, Registry.BLOCK.getOptional(id(name)).orElse(null) == block,
                "missing or incorrect block " + name);
        check(helper, Registry.ITEM.getOptional(id(name)).orElse(null) == block.asItem(),
                "missing or incorrect block item " + name);
    }

    @GameTest(template = "jurassicraft:empty")
    public static void fossilBlocksPlaceAndLoadMiningTags(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        for (Species species : Species.values()) {
            for (Block block : new Block[] {JurassicContent.FOSSILS.get(species).get(),
                    JurassicContent.ENCASED_FOSSILS.get(species).get()}) {
                helper.setBlock(position, block);
                var state = helper.getBlockState(position);
                check(helper, state.is(block), "fossil did not place: " + Registry.BLOCK.getKey(block));
                check(helper, state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                                && state.is(BlockTags.NEEDS_STONE_TOOL),
                        "fossil mining tags were not loaded: " + Registry.BLOCK.getKey(block));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void encasedFossilLootTablesLoadAndDropTheirBlock(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        for (Species species : Species.values()) {
            Block encased = JurassicContent.ENCASED_FOSSILS.get(species).get();
            ResourceLocation lootId = id("blocks/" + species.id() + "_encased_fossil");
            check(helper, helper.getLevel().getServer().getLootTables().getIds().contains(lootId),
                    "encased fossil loot table was not loaded: " + lootId);
            helper.setBlock(position, encased);
            var drops = Block.getDrops(encased.defaultBlockState(), helper.getLevel(),
                    helper.absolutePos(position), null);
            check(helper, drops.size() == 1 && drops.get(0).is(encased.asItem()) && drops.get(0).getCount() == 1,
                    "encased fossil must drop itself: " + species.id());
        }
        helper.succeed();
    }
}
