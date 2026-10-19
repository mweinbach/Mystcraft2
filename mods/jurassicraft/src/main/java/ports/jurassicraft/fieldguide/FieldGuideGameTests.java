package ports.jurassicraft.fieldguide;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FieldGuideGameTests {
    private FieldGuideGameTests() {}

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void inspectionSnapshotCarriesServerMetabolism(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        dinosaur.reduceEnergy(dinosaur.maximumEnergy() / 3);
        var snapshot = FieldGuideNetwork.Snapshot.of(dinosaur);
        if (snapshot.entityId() != dinosaur.getId() || snapshot.energy() != dinosaur.energy()
                || snapshot.maxEnergy() != dinosaur.maximumEnergy() || snapshot.water() != dinosaur.water()
                || snapshot.maxWater() != dinosaur.maximumWater() || snapshot.hungry() != dinosaur.needsFood()
                || snapshot.thirsty() != dinosaur.isThirsty() || snapshot.health() != dinosaur.getHealth()
                || snapshot.maxHealth() != dinosaur.getMaxHealth() || snapshot.ageTicks() != dinosaur.totalAgeTicks())
            helper.fail("inspection must use authoritative server values");
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        snapshot.write(bytes);
        if (!snapshot.equals(FieldGuideNetwork.Snapshot.read(bytes))) helper.fail("inspection packet lost its values");
        bytes.release();
        if (snapshot.foodPercent() < 65 || snapshot.foodPercent() > 68 || snapshot.waterPercent() != 100)
            helper.fail("inspection percentages must represent the transmitted values");
        var invalid = new FieldGuideNetwork.Snapshot(0, -1, 0, 200, 100, false, false,
                0, 1, false, false, false, 0, 48001);
        if (invalid.foodPercent() != 0 || invalid.waterPercent() != 100) helper.fail("percentages must stay within bounds");
        if (invalid.daysLived() != 2) helper.fail("age must be measured in 24000-tick days");
        helper.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void inspectionReportsWarningsAndFood(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        dinosaur.setHealth(dinosaur.getMaxHealth() / 4);
        dinosaur.setAirSupply(200);
        var healthy = FieldGuideNetwork.Snapshot.of(dinosaur);
        if (healthy.lowHealth() || healthy.lowAir() || healthy.poisoned())
            helper.fail("warning thresholds must not include healthy boundary values");
        dinosaur.setHealth(dinosaur.getMaxHealth() / 4 - 0.01F);
        dinosaur.setAirSupply(199);
        dinosaur.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        var warning = FieldGuideNetwork.Snapshot.of(dinosaur);
        if (!warning.lowHealth() || !warning.lowAir() || !warning.poisoned())
            helper.fail("inspection must report low health, low air, and poisoning");
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        warning.write(bytes);
        if (!warning.equals(FieldGuideNetwork.Snapshot.read(bytes))) helper.fail("warning packet lost its values");
        bytes.release();

        for (var species : Species.values()) {
            var animal = DinosaurRegistry.type(species).create(helper.getLevel());
            if (animal == null) { helper.fail("could not create " + species); return; }
            for (boolean baby : new boolean[]{true, false}) {
                animal.setBaby(baby);
                var info = FieldGuideNetwork.Snapshot.of(animal);
                boolean meat = animal.isFood(new ItemStack(Items.BEEF));
                if (info.acceptsMeat() != meat || info.acceptsPlants() != animal.isFood(new ItemStack(Items.CARROT))
                        || info.acceptsFish() != animal.isFood(new ItemStack(Items.COD))
                        || info.acceptsInsects() != animal.isFood(new ItemStack(BugRegistry.CRICKETS.get()))
                        || info.acceptsKrill() != animal.isFood(new ItemStack(BugRegistry.KRILL.get()))
                        || info.acceptsPlankton() != animal.isFood(new ItemStack(BugRegistry.PLANKTON.get())))
                    helper.fail("inspection diet disagrees with " + species + " feeding behavior, baby=" + baby);
                if (species == Species.MICRORAPTOR && !info.acceptsInsects())
                    helper.fail("Microraptor accepts insects at every age");
                if (species == Species.GALLIMIMUS && info.acceptsInsects() != baby)
                    helper.fail("young Gallimimus accept insects");
                if (species == Species.VELOCIRAPTOR && info.acceptsInsects() != baby)
                    helper.fail("young carnivores accept insects");
                if (species == Species.COELACANTH && (!info.acceptsFish() || !info.acceptsKrill() || !info.acceptsPlankton()))
                    helper.fail("Coelacanth accepts fish, krill and plankton");
            }
        }
        if (!FieldGuideNetwork.Snapshot.growthStage(.25F).equals("infant")
                || !FieldGuideNetwork.Snapshot.growthStage(.26F).equals("juvenile")
                || !FieldGuideNetwork.Snapshot.growthStage(.50F).equals("juvenile")
                || !FieldGuideNetwork.Snapshot.growthStage(.51F).equals("adolescent")
                || !FieldGuideNetwork.Snapshot.growthStage(.75F).equals("adolescent")
                || !FieldGuideNetwork.Snapshot.growthStage(.76F).equals("adult"))
            helper.fail("growth stages must use the upstream percentage thresholds");
        helper.succeed();
    }
}
