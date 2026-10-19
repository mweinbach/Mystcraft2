package ports.jurassicraft.entity;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Hungry herbivores eat the same classes of plants and crops registered by the original mod. */
final class GrazePlantsGoal extends MoveToBlockGoal {
    private static final Map<Block, PlantSpecies> PLANTS = new IdentityHashMap<>();
    private final DinosaurEntity dinosaur;
    GrazePlantsGoal(DinosaurEntity dinosaur) {
        super(dinosaur, .7, 16, 4);
        this.dinosaur = dinosaur;
    }
    private boolean canGraze() {
        return dinosaur.isAlive() && dinosaur.needsFood() && !dinosaur.isDinosaurSleeping()
                && !DinosaurProfile.of(dinosaur.species()).carnivore() && !dinosaur.isAquatic()
                && dinosaur.getTarget() == null && dinosaur.metabolismEnabled();
    }
    @Override public boolean canUse() { return canGraze() && super.canUse(); }
    @Override public boolean canContinueToUse() { return canGraze() && super.canContinueToUse(); }
    @Override protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos) && nutrition(level.getBlockState(pos)) > 0;
    }
    int nutrition(BlockState state) {
        if (state.isAir()) return 0;
        PlantSpecies ancient = ancientPlant(state.getBlock());
        if (ancient != null) return switch (ancient) {
            case CRY_PANSY -> 250;
            case CALAMITES, GINKGO, PSARONIUS, PHOENIX, ARAUCARIA -> 1000;
            case ORONTIUM_MACKII -> 1500;
            case WILD_ONION, WILD_POTATO, RHAMNUS_SALIFOCIFUS -> 3000;
            case SCALY_TREE_FERN, CYCAD_ZAMITES, DICKSONIA, DICROIDIUM_ZUBERI, WEST_INDIAN_LILAC,
                    SERENNA_VERIFORMANS, UMALTOLEPIS, LIRIODENDRITES, ENCEPHALARTOS, TEMPSKYA, RHACOPHYTON,
                    GRAMINIDITES_BAMBUSOIDES, HELICONIA -> 4000;
            default -> 2000;
        };
        if (state.is(PlantRegistry.PALEO_BALE_CYCADEOIDEA.get()) || state.is(PlantRegistry.PALEO_BALE_CYCAD.get())
                || state.is(PlantRegistry.PALEO_BALE_FERN.get()) || state.is(PlantRegistry.PALEO_BALE_LEAVES.get())
                || state.is(PlantRegistry.PALEO_BALE_OTHER.get())) return 5000;
        if (state.is(BlockTags.LEAVES) || state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS)
                || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)) return 2000;
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) return 3000;
        if (state.is(Blocks.HAY_BLOCK)) return 5000;
        if (state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)) return 250;
        if (state.getBlock() instanceof DoublePlantBlock) return 2000;
        if (state.is(BlockTags.FLOWERS) || state.is(Blocks.LILY_PAD)) return 500;
        if (state.is(BlockTags.SAPLINGS) || state.is(Blocks.GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.SUGAR_CANE)) return 1000;
        ItemStack blockItem = new ItemStack(state.getBlock());
        return !blockItem.isEmpty() && dinosaur.isFood(blockItem) ? 1000 : 0;
    }
    private static PlantSpecies ancientPlant(Block block) {
        if (PLANTS.isEmpty()) {
            for (PlantSpecies species : PlantSpecies.values()) {
                var plant = PlantRegistry.plant(species);
                if (plant != null && plant.isPresent()) PLANTS.put(plant.get(), species);
            }
        }
        return PLANTS.get(block);
    }
    private void applyPlantEffect(Block block) {
        PlantSpecies species = ancientPlant(block);
        if (species == null) return;
        int chance = switch (species) {
            case CALAMITES -> 5;
            case DICTYOPHYLLUM, LADINIA_SIMPLEX -> 15;
            case ENCEPHALARTOS -> 35;
            case ORONTIUM_MACKII -> 50;
            case SMALL_CYCAD, SCALY_TREE_FERN, SERENNA_VERIFORMANS, WEST_INDIAN_LILAC, AJUGINUCULA_SMITHII -> 100;
            default -> -1;
        };
        if (chance < 0 || dinosaur.getRandom().nextInt(100) > chance) return;
        MobEffectInstance effect = switch (species) {
            case AJUGINUCULA_SMITHII -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100);
            case ORONTIUM_MACKII -> new MobEffectInstance(Potions.WEAKNESS.getEffects().get(0));
            case SERENNA_VERIFORMANS, WEST_INDIAN_LILAC -> new MobEffectInstance(Potions.STRONG_POISON.getEffects().get(0));
            default -> new MobEffectInstance(Potions.POISON.getEffects().get(0));
        };
        dinosaur.addEffect(effect);
    }
    boolean graze(BlockPos pos) {
        if (!canGraze() || dinosaur.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 6) return false;
        BlockState state = dinosaur.level.getBlockState(pos);
        int amount = nutrition(state);
        if (amount <= 0 || !dinosaur.level.destroyBlock(pos, false, dinosaur)) return false;
        dinosaur.eatNutrition(amount);
        applyPlantEffect(state.getBlock());
        return true;
    }
    @Override public void tick() {
        super.tick();
        if (isReachedTarget()) graze(blockPos);
    }
}
