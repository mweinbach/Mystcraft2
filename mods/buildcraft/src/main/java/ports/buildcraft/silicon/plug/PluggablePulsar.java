package ports.buildcraft.silicon.plug;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.behaviour.PipeBehaviour;
import ports.buildcraft.transport.behaviour.WoodBehaviour;
import ports.buildcraft.transport.flow.FluidFlow;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream {@code PluggablePulsar}. While it is on, every {@link #PULSE_STAGE} ticks it pushes one MJ into the pipe.
 * Clicking toggles it; a gate's constant action holds it on and a single action queues one pulse, which waits until
 * the pipe can take the power. The stage used by the model lives here so a dedicated server never loads the renderer.
 */
public class PluggablePulsar extends PipePluggable {
    /** Ticks from the start of a pulse until power is injected. */
    public static final int PULSE_STAGE = 20;
    private static final AABB[] BOXES = faceBoxes(2, 4, 5, 11);

    private boolean manuallyEnabled;
    /** Counts from 0 to {@link #PULSE_STAGE}, when the next pulse is injected. */
    private int pulseStage;
    private int gateEnabledTicks;
    private int gateSinglePulses;
    /** The pulsing state last sent to clients. Upstream only syncs when it changes, so the auto light can lag. */
    private boolean lastPulsing;
    /** Client copy of {@link #isPulsing()}. */
    private boolean clientPulsing;
    /** Client copy of a gate holding this pulsar on. */
    private boolean autoEnabled;

    public PluggablePulsar(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        super(definition, holder, side);
    }

    public boolean manuallyEnabled() {
        return manuallyEnabled;
    }

    public int pulseStage() {
        return pulseStage;
    }

    public int gateEnabledTicks() {
        return gateEnabledTicks;
    }

    public int gateSinglePulses() {
        return gateSinglePulses;
    }

    public boolean isPulsing() {
        return manuallyEnabled || gateEnabledTicks > 0 || gateSinglePulses > 0;
    }

    public boolean clientPulsing() {
        return clientPulsing;
    }

    public boolean autoEnabled() {
        return autoEnabled;
    }

    /** Upstream's model variable {@code stage}: 0 while off, otherwise the fraction of the pulse. */
    public float stage(float partialTicks) {
        if (!clientPulsing) return 0;
        return (pulseStage + partialTicks) / PULSE_STAGE % 1;
    }

    /** A gate's constant pulsar action: held on for the next ten ticks, and refreshed while the action stays active. */
    public void enablePulsar() {
        gateEnabledTicks = 10;
    }

    /** A gate's single pulsar action: one more pulse, retried until the pipe accepts it. */
    public void addSinglePulse() {
        gateSinglePulses++;
    }

    /** Upstream's right-click: flips the manual switch and plays the lever click. */
    public void toggleManual() {
        manuallyEnabled = !manuallyEnabled;
        Level level = holder.getLevel();
        if (level != null) {
            level.playSound(null, holder.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.2F, manuallyEnabled ? 0.6F : 0.5F);
        }
        holder.setChanged();
        queueSync();
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putBoolean("manuallyEnabled", manuallyEnabled);
        tag.putInt("gateEnabledTicks", gateEnabledTicks);
        tag.putInt("gateSinglePulses", gateSinglePulses);
        tag.putInt("pulseStage", pulseStage);
    }

    @Override
    public void load(CompoundTag tag) {
        manuallyEnabled = tag.getBoolean("manuallyEnabled");
        gateEnabledTicks = tag.getInt("gateEnabledTicks");
        gateSinglePulses = tag.getInt("gateSinglePulses");
        pulseStage = Math.min(PULSE_STAGE, Math.max(0, tag.getInt("pulseStage")));
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("pulsing", isPulsing());
        tag.putBoolean("auto", gateEnabledTicks > 0 || gateSinglePulses > 0);
        tag.putBoolean("manual", manuallyEnabled);
        tag.putByte("pulseStage", (byte) pulseStage);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientPulsing = tag.getBoolean("pulsing");
        autoEnabled = tag.getBoolean("auto");
        manuallyEnabled = tag.getBoolean("manual");
        pulseStage = tag.getByte("pulseStage");
    }

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCSilicon.PULSAR_ITEM.get());
    }

    @Override
    public void onTick() {
        Level level = holder.getLevel();
        if (level != null && level.isClientSide) {
            if (clientPulsing) {
                pulseStage++;
                if (pulseStage == PULSE_STAGE) pulseStage = 0;
            } else {
                pulseStage = 0;
            }
            return;
        }
        boolean pulsing = isPulsing();
        if (pulsing) pulseStage++;
        else pulseStage = 0;
        if (gateEnabledTicks > 0) gateEnabledTicks--;
        if (pulseStage == PULSE_STAGE) {
            pulseStage = 0;
            injectPulse();
        }
        if (pulsing != lastPulsing) {
            lastPulsing = pulsing;
            queueSync();
        }
    }

    /**
     * Upstream's pulse. A queued single pulse offers the pipe's own extraction cost and is spent only when nothing is
     * left over; otherwise it stays queued. A constant pulse always offers one MJ. A pipe that does not take redstone
     * power reports the whole offer as excess, so this does not cast the behaviour.
     */
    private void injectPulse() {
        PipeBehaviour behaviour = holder.behaviour();
        if (gateSinglePulses > 0) {
            long power = Power.MJ;
            if (holder.flow() instanceof FluidFlow) power = WoodBehaviour.MJ_PER_MILLIBUCKET * 1000;
            else if (holder.flow() instanceof ItemFlow) power = WoodBehaviour.MJ_PER_ITEM;
            if (behaviour.receiveRedstonePower(power, true) == 0) behaviour.receiveRedstonePower(power, false);
            else gateSinglePulses++;
            if (gateSinglePulses > 0) gateSinglePulses--;
        } else {
            behaviour.receiveRedstonePower(Power.MJ, false);
        }
    }

    @Override
    public InteractionResult onActivate(Player player, InteractionHand hand, Vec3 hit) {
        if (holder.getLevel() != null && !holder.getLevel().isClientSide) toggleManual();
        return InteractionResult.SUCCESS;
    }
}
