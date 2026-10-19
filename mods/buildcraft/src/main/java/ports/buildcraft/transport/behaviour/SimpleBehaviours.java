package ports.buildcraft.transport.behaviour;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;

/** The stateless upstream behaviours: speed tiers, sandstone, clay, void and wooden kinesis. */
public final class SimpleBehaviours {
    /** Upstream {@code PipeBehaviourStone} constants, shared by sandstone. */
    public static final PipeBehaviour.SpeedTarget STONE_SPEED = new PipeBehaviour.SpeedTarget(0.01, 0.008);

    private SimpleBehaviours() {}

    /**
     * Upstream {@code PipeBehaviourSeparate} (cobblestone, stone, quartz): pipes of different
     * separate kinds do not connect to each other.
     */
    public static final class Separate extends PipeBehaviour {
        public enum Kind {
            COBBLESTONE(new SpeedTarget(0.01, 0.02)),
            STONE(STONE_SPEED),
            QUARTZ(new SpeedTarget(0.01, 0.002));

            final SpeedTarget speed;

            Kind(SpeedTarget speed) {
                this.speed = speed;
            }
        }

        private final Kind kind;

        public Separate(PipeBlockEntity pipe, Kind kind) {
            super(pipe);
            this.kind = kind;
        }

        @Override
        public boolean canConnect(Direction face, PipeBehaviour other) {
            return !(other instanceof Separate separate) || separate.kind == kind;
        }

        @Override
        public SpeedTarget speedTarget() {
            return kind.speed;
        }
    }

    /** Upstream {@code PipeBehaviourGold}. */
    public static final class Gold extends PipeBehaviour {
        private static final SpeedTarget SPEED = new SpeedTarget(0.25, 0.07);

        public Gold(PipeBlockEntity pipe) {
            super(pipe);
        }

        @Override
        public SpeedTarget speedTarget() {
            return SPEED;
        }
    }

    /** Upstream {@code PipeBehaviourSandstone}: connects to every pipe but never to other blocks. */
    public static final class Sandstone extends PipeBehaviour {
        public Sandstone(PipeBlockEntity pipe) {
            super(pipe);
        }

        @Override
        public boolean canConnectTile(Direction face, BlockEntity tile) {
            return false;
        }

        @Override
        public SpeedTarget speedTarget() {
            return STONE_SPEED;
        }
    }

    /** Upstream {@code PipeBehaviourClay}: prefers inventories and tanks over further pipes. */
    public static final class Clay extends PipeBehaviour {
        public Clay(PipeBlockEntity pipe) {
            super(pipe);
        }

        private void preferTiles(SideCheck check) {
            for (Direction face : Direction.values()) {
                if (pipe.connectedType(face) == PipeBlockEntity.ConnectedType.TILE) check.increasePriority(face, 100);
            }
        }

        @Override
        public void itemSideCheck(SideCheck check) {
            preferTiles(check);
        }

        @Override
        public void fluidSideCheck(SideCheck check) {
            preferTiles(check);
        }
    }

    /** Upstream {@code PipeBehaviourVoid}: destroys everything that reaches the centre. */
    public static final class Void extends PipeBehaviour {
        public Void(PipeBlockEntity pipe) {
            super(pipe);
        }

        @Override
        public ItemStack onItemReachCenter(ItemStack stack, Direction from) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onMoveFluidToCentre(int[] fluidEnteringCentre) {
            Arrays.fill(fluidEnteringCentre, 0);
        }
    }

    /** Upstream {@code PipeBehaviourWoodPower}: the power input pipe; wooden kinesis pipes do not chain. */
    public static final class WoodPower extends PipeBehaviour {
        public WoodPower(PipeBlockEntity pipe) {
            super(pipe);
        }

        @Override
        public boolean canConnect(Direction face, PipeBehaviour other) {
            return !(other instanceof WoodPower);
        }

        /** Upstream texture index 1: a connected block that cannot receive power (an engine). */
        @Override
        public boolean isSpecialSide(Direction face) {
            if (pipe.connectedType(face) != PipeBlockEntity.ConnectedType.TILE || !pipe.hasLevel()) return false;
            EnergyPort port = Ports.energy(pipe.getLevel(), pipe.getBlockPos().relative(face), face.getOpposite());
            return port == null || !port.canReceive();
        }
    }
}
