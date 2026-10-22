package ports.jurassicraft.client;

import ports.jurassicraft.entity.DinosaurEntity;

/** Chooses the pose-engine action from the same priorities the renderer used to inline. */
public final class DinosaurPoseSelection {
    public record Cue(String action, int start, float elapsed) {}

    private DinosaurPoseSelection() {}

    public static Cue select(DinosaurEntity entity, float age) {
        String combat = switch (entity.combatAnimation()) {
            case SPIT -> "DILOPHOSAURUS_SPIT";
            case PREPARE_LEAP -> "PREPARE_LEAP";
            case LEAP -> "LEAP";
            case LEAP_LAND -> "LEAP_LAND";
            default -> null;
        };
        if (entity.isCombatPoseInterrupted()) combat = null;
        String pose = entity.poseAnimation().isEmpty() ? null : entity.poseAnimation();
        if (entity.isCarcass() || entity.deathTime > 0) {
            int start = entity.isCarcass() ? entity.carcassAnimationStart() : entity.tickCount - entity.deathTime;
            float elapsed = entity.isCarcass() ? Math.max(0, age - start) : entity.deathTime;
            return new Cue("DYING", start, elapsed);
        }
        if (entity.isDinosaurSleeping() || entity.isOrderedToSit() || entity.isNaturallyResting())
            return new Cue(entity.isDinosaurSleeping() ? "SLEEPING" : "RESTING", 0, 0);
        if (entity.isInjuryAnimating())
            return new Cue("INJURED", entity.injuryAnimationStart(), entity.injuryAnimationTime(age));
        if (entity.isForcedPose() || (combat == null && pose != null)) {
            float elapsed = entity.isBirthing() && !entity.isForcedPose()
                    ? entity.birthAnimationTime(age) : Math.max(0, age - entity.poseAnimationStart());
            return new Cue(pose, entity.poseAnimationStart(), elapsed);
        }
        if (combat != null)
            return new Cue(combat, entity.combatAnimationStart(), Math.max(0, age - entity.combatAnimationStart()));
        if (entity.isGliding()) return new Cue("GLIDING", 0, 0);
        // The synced attack timer is the original ATTACKING pose. Vanilla swingTime is only the hit cue.
        if (entity.isAttackAnimating())
            return new Cue("ATTACKING", entity.attackAnimationStart(), entity.attackAnimationTime(age));
        return new Cue("IDLE", 0, 0);
    }
}
