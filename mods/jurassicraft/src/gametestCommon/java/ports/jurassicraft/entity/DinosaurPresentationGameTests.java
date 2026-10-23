package ports.jurassicraft.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import ports.jurassicraft.content.Species;

public final class DinosaurPresentationGameTests {
    private DinosaurPresentationGameTests() {}

    private static final class ListeningDinosaur extends DinosaurEntity {
        private final List<SoundEvent> heard = new ArrayList<>();
        ListeningDinosaur(EntityType<? extends DinosaurEntity> type, Level level) { super(type, level); }
        @Override public void playSound(SoundEvent sound, float volume, float pitch) { heard.add(sound); }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void headTrackingMatchesOriginalClampAndDistribution(GameTestHelper helper) {
        float expectedYaw = (float) Math.toRadians(45) / 6;
        require(helper, Math.abs(DinosaurLookTracking.yaw(90, 1.5f, 4) - expectedYaw) < .00001f,
                "rex head rotation should clamp yaw and distribute it across four parts");
        require(helper, Math.abs(DinosaurLookTracking.yaw(-90, 1.5f, 4) + expectedYaw) < .00001f,
                "yaw should clamp symmetrically");
        float expectedPitch = (float) Math.toRadians(-160) / 4;
        require(helper, Math.abs(DinosaurLookTracking.pitch(200, 2, 2) - expectedPitch) < .00001f,
                "pitch should wrap and follow the species divisor");
        require(helper, DinosaurLookTracking.yaw(45, 1, 0) == 0 && DinosaurLookTracking.pitch(45, 1, 0) == 0,
                "animals without a head chain should not rotate");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void speedAndBreathing(GameTestHelper helper) {
        var dinosaur = new ListeningDinosaur(DinosaurRegistry.type(Species.VELOCIRAPTOR), helper.getLevel());
        dinosaur.setNoAi(true);
        double normal = dinosaur.getAttributeValue(Attributes.MOVEMENT_SPEED);
        dinosaur.tickCount = 1;
        dinosaur.setSpeed((float) normal);
        DinosaurPresentation.serverTick(dinosaur);
        require(helper, !dinosaur.isAnimationRunning(), "normal AI speed should select the walking gait");
        dinosaur.setSpeed((float) (normal * 1.25));
        DinosaurPresentation.serverTick(dinosaur);
        require(helper, dinosaur.isAnimationRunning(), "faster AI speed should synchronize the running gait");
        dinosaur.setSpeed((float) normal);
        DinosaurPresentation.serverTick(dinosaur);
        require(helper, !dinosaur.isAnimationRunning(), "returning to normal speed should restore walking");
        dinosaur.tickCount = 62;
        DinosaurPresentation.serverTick(dinosaur);
        require(helper, dinosaur.heard.equals(List.of(DinosaurRegistry.sound(Species.VELOCIRAPTOR, "breathing"))),
                "a living raptor should breathe exactly once every 62 ticks");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void forcedSoundsAndStomps(GameTestHelper helper) {
        var dinosaur = new ListeningDinosaur(DinosaurRegistry.type(Species.TYRANNOSAURUS), helper.getLevel());
        require(helper, dinosaur.playPose("ROARING", 2, true)
                && dinosaur.heard.equals(List.of(DinosaurRegistry.sound(Species.TYRANNOSAURUS, "roar3"))),
                "the third forced roar must play its matching original sound");
        require(helper, !dinosaur.playPose("ROARING", 3, true) && dinosaur.heard.size() == 1,
                "unsupported variants must not play sounds");
        require(helper, dinosaur.playPose("DRINKING", 0, true)
                && dinosaur.heard.get(1) == DinosaurRegistry.sound(Species.TYRANNOSAURUS, "drinking"),
                "the forced drinking pose must play its sound");
        dinosaur.setOnGround(true);
        DinosaurPresentation.playStep(dinosaur);
        require(helper, dinosaur.heard.get(2) == DinosaurRegistry.sound(Species.TYRANNOSAURUS, "stomp"),
                "a grounded tyrannosaurus should play its stomp");
        dinosaur.setOnGround(false);
        DinosaurPresentation.playStep(dinosaur);
        require(helper, dinosaur.heard.size() == 3, "an airborne dinosaur must not stomp");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void nearbyAnimalsCallAndMatureRexRoars(GameTestHelper helper) {
        var caller = new ListeningDinosaur(DinosaurRegistry.type(Species.VELOCIRAPTOR), helper.getLevel());
        var neighbor = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        var rex = new ListeningDinosaur(DinosaurRegistry.type(Species.TYRANNOSAURUS), helper.getLevel());
        if (neighbor == null) { helper.fail("could not create neighboring raptor"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        caller.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        neighbor.moveTo(pos.getX() + 2, pos.getY(), pos.getZ(), 0, 0);
        helper.getLevel().addFreshEntity(neighbor);
        try {
            require(helper, DinosaurPresentation.call(caller) && caller.poseAnimation().equals("CALLING")
                    && caller.heard.equals(List.of(DinosaurRegistry.sound(Species.VELOCIRAPTOR, "call"))),
                    "nearby same-species raptors must call using the matching pose and sound");
            require(helper, !DinosaurPresentation.call(caller) && caller.heard.size() == 1,
                    "an active pose must not be interrupted or play the call twice");
            rex.setBaby(true);
            require(helper, !DinosaurPresentation.roar(rex) && rex.heard.isEmpty(), "young rex must not roar");
            rex.setBaby(false);
            require(helper, DinosaurPresentation.roar(rex) && rex.poseAnimation().equals("ROARING")
                    && rex.heard.equals(List.of(DinosaurRegistry.sound(Species.TYRANNOSAURUS, "roar1"))),
                    "mature rex must use the original roar pose and sound");
        } finally {
            neighbor.discard();
        }
        helper.succeed();
    }
}
