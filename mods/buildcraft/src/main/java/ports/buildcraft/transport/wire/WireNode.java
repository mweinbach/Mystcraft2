package ports.buildcraft.transport.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Upstream {@code WireNode}: one corner of one pipe. */
public record WireNode(BlockPos pos, WirePart part) {
    /** The neighbouring corner towards {@code face}: another corner of the same pipe, or one of the next pipe. */
    public WireNode offset(Direction face) {
        int nx = (part.x == Direction.AxisDirection.POSITIVE ? 1 : 0) + face.getStepX();
        int ny = (part.y == Direction.AxisDirection.POSITIVE ? 1 : 0) + face.getStepY();
        int nz = (part.z == Direction.AxisDirection.POSITIVE ? 1 : 0) + face.getStepZ();
        WirePart next = WirePart.get(nx, ny, nz);
        if (nx < 0 || ny < 0 || nz < 0 || nx > 1 || ny > 1 || nz > 1) return new WireNode(pos.relative(face), next);
        return new WireNode(pos, next);
    }
}
