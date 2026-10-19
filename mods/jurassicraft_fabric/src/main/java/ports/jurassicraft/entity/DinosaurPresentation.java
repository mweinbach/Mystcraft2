package ports.jurassicraft.entity;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.content.Species;

/** Server-side presentation cues; no client classes are loaded on a dedicated server. */
final class DinosaurPresentation {
    private DinosaurPresentation() {}

    static void serverTick(DinosaurEntity dinosaur) {
        if (dinosaur.level.isClientSide) return;
        // The original synchronizes whether the current AI speed exceeds the normal movement speed.
        dinosaur.setAnimationRunning(dinosaur.getSpeed() > dinosaur.getAttributeValue(Attributes.MOVEMENT_SPEED) + 0.00001);
        if (!dinosaur.isAlive()) return;
        if (dinosaur.tickCount % 62 == 0 && !dinosaur.isDinosaurSleeping()) {
            SoundEvent breathing = DinosaurRegistry.sound(dinosaur.species(), "breathing");
            if (breathing != null) dinosaur.playSound(breathing, volume(dinosaur), pitch(dinosaur));
        }
        if (!canVocalize(dinosaur)) return;
        if (dinosaur.getRandom().nextDouble() < .003 && call(dinosaur)) return;
        if (dinosaur.growth() > .75f && dinosaur.getRandom().nextDouble() < .002) roar(dinosaur);
    }

    private static boolean canVocalize(DinosaurEntity dinosaur) {
        return !dinosaur.isNoAi() && !dinosaur.isDinosaurSleeping() && !dinosaur.isOrderedToSit()
                && !dinosaur.isNaturallyResting() && !dinosaur.isInWater() && dinosaur.getTarget() == null
                && dinosaur.poseAnimation().isEmpty() && !dinosaur.isAttackAnimating()
                && dinosaur.combatAnimation() == DinosaurEntity.CombatAnimation.NONE
                && dinosaur.swingTime == 0 && dinosaur.hurtTime == 0;
    }

    static boolean call(DinosaurEntity dinosaur) {
        if (dinosaur.level.isClientSide || !dinosaur.isAlive() || !canVocalize(dinosaur)
                || DinosaurAnimationData.duration(dinosaur.species(), "CALLING") == 0) return false;
        AABB bounds = new AABB(dinosaur.getX() - 50, dinosaur.getY() - 10, dinosaur.getZ() - 50,
                dinosaur.getX() + 50, dinosaur.getY() + 10, dinosaur.getZ() + 50);
        boolean hasNeighbor = !dinosaur.level.getEntitiesOfClass(DinosaurEntity.class, bounds,
                other -> other != dinosaur && other.getType() == dinosaur.getType()).isEmpty();
        if (!hasNeighbor || !dinosaur.playPose("CALLING", 0, false)) return false;
        playVocalization(dinosaur, animationSound(dinosaur.species(), "CALLING", 0));
        return true;
    }

    static boolean roar(DinosaurEntity dinosaur) {
        if (dinosaur.level.isClientSide || !dinosaur.isAlive() || !canVocalize(dinosaur)
                || dinosaur.growth() <= .75f || !dinosaur.playPose("ROARING", 0, false)) return false;
        playVocalization(dinosaur, animationSound(dinosaur.species(), "ROARING", 0));
        return true;
    }

    private static void playVocalization(DinosaurEntity dinosaur, @Nullable SoundEvent sound) {
        if (sound != null) dinosaur.playSound(sound, volume(dinosaur) + 1.25f, pitch(dinosaur));
    }

    static void playForcedPose(DinosaurEntity dinosaur, String pose, int variant) {
        SoundEvent sound = animationSound(dinosaur.species(), pose, variant);
        if (sound != null) dinosaur.playSound(sound, 1, 1);
    }

    @Nullable static SoundEvent animationSound(Species species, String pose, int variant) {
        String kind = switch (pose.toUpperCase(Locale.ROOT)) {
            case "SPEAK" -> "ambient";
            case "INJURED" -> "hurt";
            case "DYING" -> "death";
            case "CALLING" -> switch (species) {
                case VELOCIRAPTOR, PARASAUROLOPHUS, TYRANNOSAURUS -> "call";
                case BRACHIOSAURUS, DILOPHOSAURUS, GALLIMIMUS, MICRORAPTOR, MUSSAURUS, TRICERATOPS -> "ambient";
                default -> null;
            };
            case "ROARING" -> species == Species.TYRANNOSAURUS && variant >= 0 && variant < 3
                    ? "roar" + (variant + 1) : null;
            case "DRINKING" -> species == Species.TYRANNOSAURUS ? "drinking" : null;
            case "ATTACKING" -> switch (species) {
                case TYRANNOSAURUS, VELOCIRAPTOR, MICRORAPTOR, MUSSAURUS -> "attack";
                default -> null;
            };
            default -> null;
        };
        return kind == null ? null : DinosaurRegistry.sound(species, kind);
    }

    static void playStep(DinosaurEntity dinosaur) {
        if (dinosaur.level.isClientSide || dinosaur.species() != Species.TYRANNOSAURUS
                || !dinosaur.isAlive() || !dinosaur.isOnGround() || dinosaur.isInWater()) return;
        SoundEvent sound = DinosaurRegistry.sound(dinosaur.species(), "stomp");
        if (sound != null) dinosaur.playSound(sound, .1f + .9f * dinosaur.growth(), pitch(dinosaur));
    }

    private static float volume(DinosaurEntity dinosaur) { return .4f + 1.6f * dinosaur.growth(); }
    private static float pitch(DinosaurEntity dinosaur) {
        return 2.5f - 1.5f * dinosaur.growth() + (dinosaur.getRandom().nextFloat() - .5f) * .125f;
    }
}
