package ports.buildcraft.transport.behaviour;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code PipeBehaviourDirectional}: a pipe with one selected face, rotated with a wrench. */
public abstract class DirectionalBehaviour extends PipeBehaviour {
    @Nullable
    protected Direction currentDir;

    protected DirectionalBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    protected abstract boolean canFaceDirection(@Nullable Direction dir);

    @Nullable
    public Direction currentDir() {
        return currentDir;
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        if (!Wrenches.isWrench(player.getItemInHand(hand))) return InteractionResult.PASS;
        if (part == null) advanceFacing();
        else if (part != currentDir && canFaceDirection(part)) setCurrentDir(part);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onTick() {
        if (!canFaceDirection(currentDir) && !advanceFacing()) setCurrentDir(null);
    }

    /** @return true if the facing changed. */
    public boolean advanceFacing() {
        Direction current = currentDir;
        for (int i = 0; i < 6; i++) {
            current = Wrenches.nextFacing(current);
            if (canFaceDirection(current)) {
                setCurrentDir(current);
                return true;
            }
        }
        return false;
    }

    public void setCurrentDir(@Nullable Direction direction) {
        if (currentDir == direction) return;
        currentDir = direction;
        pipe.setChanged();
        pipe.refreshState();
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putByte("currentDir", (byte) (currentDir == null ? -1 : currentDir.get3DDataValue()));
    }

    @Override
    public void load(CompoundTag tag) {
        byte value = tag.contains("currentDir") ? tag.getByte("currentDir") : -1;
        currentDir = value < 0 ? null : Direction.from3DDataValue(value);
    }
}
