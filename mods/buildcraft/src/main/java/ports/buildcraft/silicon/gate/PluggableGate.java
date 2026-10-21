package ports.buildcraft.silicon.gate;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;
import ports.buildcraft.transport.wire.WireEmitter;

/**
 * Upstream {@code PluggableGate}: a gate on one face of a pipe. Clicking it with a gate copier copies or
 * pastes its configuration; clicking it otherwise opens its GUI. Placement advancements are not ported.
 */
public class PluggableGate extends PipePluggable implements WireEmitter {
    private static final AABB[] BOXES = faceBoxes(2, 4, 5, 11);

    private GateLogic logic;

    public PluggableGate(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        this(definition, holder, side, GateVariant.BASIC);
    }

    public PluggableGate(PluggableDefinition definition, PipeBlockEntity holder, Direction side, GateVariant variant) {
        super(definition, holder, side);
        logic = new GateLogic(this, variant);
    }

    public GateLogic logic() {
        return logic;
    }

    // Saving, loading and client data

    @Override
    public void save(CompoundTag tag) {
        tag.put("data", logic.writeToNbt());
    }

    @Override
    public void load(CompoundTag tag) {
        readLogic(tag.getCompound("data"));
    }

    @Override
    public void writeClient(CompoundTag tag) {
        save(tag);
        tag.putBoolean("on", logic.isOn);
    }

    @Override
    public void readClient(CompoundTag tag) {
        load(tag);
        logic.isOn = tag.getBoolean("on");
    }

    private void readLogic(CompoundTag data) {
        GateVariant variant = GateVariant.readFromNbt(data.getCompound("variant"));
        if (!variant.equals(logic.variant)) logic = new GateLogic(this, variant);
        logic.readFromNbt(data);
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public boolean canConnectToRedstone(@Nullable Direction to) {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCSilicon.gateItem(logic.variant));
    }

    @Override
    public void onTick() {
        logic.onTick();
    }

    @Override
    public boolean isEmitting(DyeColor colour) {
        return logic.isEmitting(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        logic.emitWire(colour);
    }

    @Override
    public InteractionResult onActivate(Player player, InteractionHand hand, Vec3 hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!interactWithCopier(player, player.getMainHandItem()) && !interactWithCopier(player, player.getOffhandItem())) {
                GateMenu.open(serverPlayer, this);
            }
        }
        return InteractionResult.sidedSuccess(holder.getLevel() != null && holder.getLevel().isClientSide);
    }

    /**
     * Upstream {@code interactWithCopier}. Upstream's check for an empty gate counted the saved tags, which
     * always include the variant and connections, so it never fired; this checks the configuration instead.
     */
    private boolean interactWithCopier(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof GateCopierItem)) return false;
        CompoundTag stored = GateCopierItem.getCopiedGateData(stack);
        if (stored != null) {
            logic.readConfigData(stored);
            queueSync();
            holder.setChanged();
            player.displayClientMessage(Component.translatable("chat.gateCopier.gatePasted"), true);
            return true;
        }
        if (logic.isEmpty()) {
            player.displayClientMessage(Component.translatable("chat.gateCopier.noInformation"), true);
            return false;
        }
        GateCopierItem.setCopiedGateData(stack, logic.writeConfigData());
        player.displayClientMessage(Component.translatable("chat.gateCopier.gateCopied"), true);
        return true;
    }
}
