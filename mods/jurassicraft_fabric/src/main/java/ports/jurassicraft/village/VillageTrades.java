package ports.jurassicraft.village;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Original career trade pools, with randomized species-preserving fossil items. */
public final class VillageTrades {
    private VillageTrades() {}
    private static final String[] BONES = {"pelvis", "skull", "tooth", "tail_vertebrae", "shoulder", "ribcage", "neck_vertebrae", "hind_leg_bones", "front_leg_bones", "anal_fin", "caudal_fin", "first_dorsal_fin", "pectoral_fin_bones", "pelvic_fin_bones", "second_dorsal_fin", "spine", "teeth", "arm_bones", "leg_bones", "neck", "foot_bones", "shoulder_bone", "horn", "claw", "skull", "pelvis", "teeth"};
    private static final int[] COSTS = {4,6,4,5,4,4,6,4,4,5,4,4,5,4,4,4,3,5,4,4,6,4,4,5,4,2,2};

    public static void init() {
        add(VillageRegistry.GENETICIST.get(), 1, VillageTrades::geneticistCommon, true);
        add(VillageRegistry.GENETICIST.get(), 2, VillageTrades::geneticistAdvanced, false);
        add(VillageRegistry.PALEONTOLOGIST.get(), 1, VillageTrades::paleontologistCommon, false);
        add(VillageRegistry.PALEONTOLOGIST.get(), 2, VillageTrades::paleontologistCommon, false);
        add(VillageRegistry.PALEONTOLOGIST.get(), 3, VillageTrades::paleontologistSpecial, true);
    }
    private interface Factory { MerchantOffer create(RandomSource random); }
    private static void add(VillagerProfession profession, int level, Factory factory, boolean optionalSecond) {
        TradeOfferHelper.registerVillagerOffers(profession, level, trades -> {
            trades.add((merchant, random) -> factory.create(random));
            if (optionalSecond) trades.add((merchant, random) -> random.nextInt(10) == 0 ? factory.create(random) : null);
        });
    }
    private static MerchantOffer trade(ItemStack input, ItemStack output, int xp) { return new MerchantOffer(input, output, 12, xp, 0.05F); }
    private static MerchantOffer sale(RandomSource r, int min, int range, Supplier<? extends Item> item, int count, int more, int xp) {
        return trade(new ItemStack(Items.EMERALD, min + r.nextInt(range)), new ItemStack(item.get(), count + r.nextInt(more)), xp);
    }
    private static MerchantOffer purchase(RandomSource r, Supplier<? extends Item> item, int count, int more, int emeralds, int extra, int xp) {
        return trade(new ItemStack(item.get(), count + r.nextInt(more)), new ItemStack(Items.EMERALD, emeralds + r.nextInt(extra)), xp);
    }
    static MerchantOffer geneticistCommon(RandomSource r) {
        return switch (r.nextInt(9)) {
            case 0 -> sale(r, 1, 2, ComponentsRegistry.PETRI_DISH, 8, 5, 1);
            case 1 -> sale(r, 1, 1, ComponentsRegistry.LIQUID_AGAR, 10, 5, 1);
            case 2 -> sale(r, 1, 1, ComponentsRegistry.EMPTY_TEST_TUBE, 14, 4, 1);
            case 3 -> sale(r, 3, 1, GeneticsRegistry.STORAGE_DISC, 14, 4, 1);
            case 4 -> sale(r, 1, 1, ComponentsRegistry.EMPTY_SYRINGE, 10, 5, 1);
            case 5 -> sale(r, 1, 2, ComponentsRegistry.DNA_NUCLEOTIDES, 14, 4, 1);
            case 6 -> purchase(r, ComponentsRegistry.DNA_NUCLEOTIDES, 14, 4, 1, 1, 1);
            case 7 -> purchase(r, () -> Items.GLASS, 10, 10, 1, 2, 1);
            default -> purchase(r, ComponentsRegistry.BASIC_CIRCUIT, 5, 5, 1, 3, 1);
        };
    }
    static MerchantOffer geneticistAdvanced(RandomSource r) {
        return switch (r.nextInt(7)) {
            case 0 -> sale(r, 6, 2, () -> DnaMachineRegistry.EXTRACTOR.get().asItem(), 1, 1, 5);
            case 1 -> sale(r, 5, 2, () -> DnaMachineRegistry.SEQUENCER.get().asItem(), 1, 1, 5);
            case 2 -> sale(r, 5, 3, () -> DnaMachineRegistry.COMBINATOR.get().asItem(), 1, 1, 5);
            case 3 -> sale(r, 5, 2, () -> DnaMachineRegistry.SYNTHESIZER.get().asItem(), 1, 1, 5);
            case 4 -> sale(r, 4, 4, () -> HatcheryRegistry.block(HatcheryRegistry.Machine.EMBRYONIC).asItem(), 1, 1, 5);
            case 5 -> purchase(r, ComponentsRegistry.ADVANCED_CIRCUIT, 2, 2, 2, 2, 5);
            default -> purchase(r, ComponentsRegistry.ADVANCED_CIRCUIT, 10, 5, 1, 1, 5);
        };
    }
    static MerchantOffer paleontologistCommon(RandomSource r) {
        return switch (r.nextInt(3)) {
            case 0 -> sale(r, 1, 4, () -> Items.IRON_SHOVEL, 1, 1, 1);
            case 1 -> sale(r, 1, 2, JurassicContent.PLASTER_AND_BANDAGE, 1, 3, 1);
            default -> sale(r, 1, 4, JurassicContent.PLANT_FOSSIL, 1, 1, 1);
        };
    }
    static MerchantOffer paleontologistSpecial(RandomSource r) {
        int pick = r.nextInt(BONES.length);
        String bone = BONES[pick];
        List<Species> species = new ArrayList<>();
        for (Species candidate : Species.values()) if (candidate.bones().contains(bone)) species.add(candidate);
        ItemStack fossil = new ItemStack(JurassicContent.BONES.get(species.get(r.nextInt(species.size()))).get(bone).get());
        ItemStack emeralds = new ItemStack(Items.EMERALD, 1 + r.nextInt(COSTS[pick]));
        return pick < 24 ? trade(emeralds, fossil, 10) : trade(fossil, emeralds, 10);
    }
}
