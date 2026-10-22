package ports.jurassicraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.jurassicraft.client.DinosaurChainMath;
import ports.jurassicraft.client.DinosaurPoseSelection;
import ports.jurassicraft.client.DinosaurProceduralPose;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.entity.DinosaurTailSway;

/** Shared checks for species procedural motion and the original attack pose. */
public final class DinosaurAnimationChecks {
    private DinosaurAnimationChecks() {}

    public static void verify(GameTestHelper helper) {
        DinosaurChainMath.verify();
        DinosaurTailSway.verify();
        DinosaurProceduralPose.verifyPackaged();
        DinosaurEntity target = create(helper, Species.MUSSAURUS);
        target.setInvulnerable(true);
        DinosaurEntity rex = create(helper, Species.TYRANNOSAURUS);
        rex.doHurtTarget(target);
        rex.swingTime = 6;
        var attack = DinosaurPoseSelection.select(rex, rex.tickCount + 9);
        if (!rex.isAttackAnimating() || !"ATTACKING".equals(attack.action()) || Math.abs(attack.elapsed() - 9f) > .001f)
            throw new AssertionError("attack pose follows the species timer, not swingTime: " + attack);
        DinosaurEntity gallimimus = create(helper, Species.GALLIMIMUS);
        gallimimus.doHurtTarget(target);
        gallimimus.swingTime = 6;
        var idle = DinosaurPoseSelection.select(gallimimus, gallimimus.tickCount);
        if (gallimimus.isAttackAnimating() || !"IDLE".equals(idle.action()))
            throw new AssertionError("a species without an attack pose must not borrow the swing: " + idle);
    }

    private static DinosaurEntity create(GameTestHelper helper, Species species) {
        DinosaurEntity dinosaur = DinosaurRegistry.type(species).create(helper.getLevel());
        if (dinosaur == null) throw new IllegalStateException("Could not create " + species);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        dinosaur.setBaby(false);
        return dinosaur;
    }
}
