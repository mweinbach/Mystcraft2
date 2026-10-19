package ports.jurassicraft.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.fence.FenceWireBlock;

/** A shocked dinosaur chooses a nearby destination clear of fence wires. */
final class EscapeDinosaurFenceGoal extends Goal {
    private final DinosaurEntity dinosaur;
    EscapeDinosaurFenceGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Override public boolean canUse() { return dinosaur.isAlive() && dinosaur.wireTicks() > 0; }
    @Override public void start() {
        dinosaur.getNavigation().stop();
        for (int attempt = 0; attempt < 20; attempt++) {
            Vec3 destination = DefaultRandomPos.getPos(dinosaur, 8, 4);
            if (destination == null) continue;
            BlockPos center = new BlockPos(destination);
            if (!dinosaur.level.hasChunkAt(center)) continue;
            boolean wire = false;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                if (!dinosaur.level.hasChunkAt(pos) || dinosaur.level.getBlockState(pos).getBlock() instanceof FenceWireBlock) {
                    wire = true;
                    break;
                }
            }
            if (!wire && dinosaur.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.4)) break;
        }
    }
    @Override public boolean canContinueToUse() { return !dinosaur.getNavigation().isDone(); }
    @Override public void stop() { dinosaur.getNavigation().stop(); }
}
