package ports.jurassicraft.goat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.worldgen.NaturalSpawns;

public final class GoatGameTests implements FabricGameTest {
    public GoatGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void milkingRequiresAdultNannyAndGrassRestoresMilk(GameTestHelper helper) {
        JurassicGoat goat = helper.spawnWithNoFreeWill(GoatRegistry.GOAT.get(), new BlockPos(1, 1, 1));
        goat.setBilly(false);
        goat.setBaby(false);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.MILK_BUCKET) && goat.isMilked(), "nanny must give milk");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.BUCKET), "nanny must not give milk twice before eating");
        goat.ate();
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.MILK_BUCKET), "eating must restore milk");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.ate(); goat.setBilly(true);
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.BUCKET), "billy must not give milk");
        goat.setBilly(false); goat.setBaby(true);
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.BUCKET), "kid must not give milk");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void breedingAndSavedState(GameTestHelper helper) {
        JurassicGoat first = helper.spawnWithNoFreeWill(GoatRegistry.GOAT.get(), new BlockPos(0, 1, 1));
        JurassicGoat second = helper.spawnWithNoFreeWill(GoatRegistry.GOAT.get(), new BlockPos(2, 1, 1));
        first.setBaby(false); second.setBaby(false); first.setBilly(true); second.setBilly(false);
        first.setInLove(null); second.setInLove(null);
        require(helper, first.canMate(second), "opposite-sex adults in love should breed");
        second.setBilly(true);
        require(helper, !first.canMate(second), "same-sex goats must not breed");
        require(helper, first.getBreedOffspring(helper.getLevel(), second) != null, "goat breeding must create a kid");
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Billy", true); tag.putInt("Variant", 2); tag.putBoolean("Milked", true);
        first.readAdditionalSaveData(tag);
        CompoundTag saved = new CompoundTag();
        first.addAdditionalSaveData(saved);
        require(helper, saved.getBoolean("Billy") && saved.getInt("Variant") == 2 && saved.getBoolean("Milked"), "goat state did not persist");
        require(helper, first.variant().equals("jpog"), "variant is not preserved");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void naturalSpawningUsesPigBiomesAndConfig(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        var desert = biomes.getHolderOrThrow(Biomes.DESERT);
        require(helper, !JurassicConfig.NATURAL_GOAT_SPAWNING.defaultValue(), "goat spawning must default off");
        boolean previous = JurassicConfig.NATURAL_GOAT_SPAWNING.get();
        try {
            JurassicConfig.NATURAL_GOAT_SPAWNING.set(false);
            require(helper, goatSpawn() == null, "disabled goat spawning must not add a spawn entry");
            JurassicConfig.NATURAL_GOAT_SPAWNING.set(true);
            var goat = goatSpawn();
            require(helper, goat != null && goat.rule().accepts(view(plains)), "pig biomes should permit opt-in goats");
            require(helper, !goat.rule().accepts(view(desert)), "a biome without pigs must not receive goats");
        } finally {
            JurassicConfig.NATURAL_GOAT_SPAWNING.set(previous);
        }
        require(helper, Registry.ITEM.get(new net.minecraft.resources.ResourceLocation(PortMod.MOD_ID, "goat_spawn_egg")) == GoatRegistry.EGG.get(),
                "goat spawn egg must use its stable registry id");
        helper.succeed();
    }

    private static NaturalSpawns.Entry goatSpawn() {
        return NaturalSpawns.entries().stream().filter(entry -> entry.type() == GoatRegistry.GOAT.get()).findFirst().orElse(null);
    }

    private static NaturalSpawns.BiomeView view(Holder<Biome> biome) {
        return new NaturalSpawns.BiomeView() {
            @Override public boolean is(TagKey<Biome> tag) { return biome.is(tag); }
            @Override public boolean spawns(MobCategory category, EntityType<?> type) {
                return biome.value().getMobSettings().getMobs(category).unwrap().stream().anyMatch(spawn -> spawn.type == type);
            }
        };
    }
}
