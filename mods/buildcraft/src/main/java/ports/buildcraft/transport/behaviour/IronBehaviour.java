package ports.buildcraft.transport.behaviour;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;

/** Upstream {@code PipeBehaviourIron}: everything leaves through the selected face only. */
public class IronBehaviour extends DirectionalBehaviour {
    public IronBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    protected boolean canFaceDirection(@Nullable Direction dir) {
        return dir != null && pipe.isConnected(dir);
    }

    @Override
    public boolean isSpecialSide(Direction face) {
        return face == currentDir;
    }

    @Override
    public void itemSideCheck(SideCheck check) {
        restrict(check);
    }

    @Override
    public void fluidSideCheck(SideCheck check) {
        restrict(check);
    }

    private void restrict(SideCheck check) {
        if (currentDir == null) check.disallowAll();
        else check.disallowAllExcept(currentDir);
    }

    @Override
    public boolean canBounce() {
        return true;
    }

    @Override
    public boolean canInsertFluid(Direction from) {
        return from != currentDir;
    }
}
