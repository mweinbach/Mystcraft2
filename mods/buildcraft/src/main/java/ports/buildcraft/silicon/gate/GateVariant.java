package ports.buildcraft.silicon.gate;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Upstream {@code GateVariant}. Upstream kept every variant in one item's NBT; the port has one item per
 * variant, named after {@link #variantName()}, and {@link #VALUES} lists them in upstream's creative order.
 */
public record GateVariant(EnumGateLogic logic, EnumGateMaterial material, EnumGateModifier modifier) {
    public static final GateVariant BASIC = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);
    public static final List<GateVariant> VALUES;

    static {
        List<GateVariant> values = new ArrayList<>();
        values.add(BASIC);
        for (EnumGateMaterial material : EnumGateMaterial.VALUES) {
            if (!material.canBeModified) continue;
            for (EnumGateLogic logic : EnumGateLogic.VALUES) {
                for (EnumGateModifier modifier : EnumGateModifier.VALUES) values.add(new GateVariant(logic, material, modifier));
            }
        }
        VALUES = List.copyOf(values);
    }

    public static GateVariant readFromNbt(CompoundTag tag) {
        return new GateVariant(EnumGateLogic.getByOrdinal(tag.getByte("logic")), EnumGateMaterial.getByOrdinal(tag.getByte("material")),
                EnumGateModifier.getByOrdinal(tag.getByte("modifier")));
    }

    public CompoundTag writeToNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("logic", (byte) logic.ordinal());
        tag.putByte("material", (byte) material.ordinal());
        tag.putByte("modifier", (byte) modifier.ordinal());
        return tag;
    }

    public int numSlots() {
        return material.numSlots / modifier.slotDivisor;
    }

    public int numTriggerArgs() {
        return modifier.triggerParams;
    }

    public int numActionArgs() {
        return modifier.actionParams;
    }

    public String variantName() {
        return material.canBeModified ? material.tag + "_" + logic.tag + "_" + modifier.tag : material.tag;
    }

    public Component localizedName() {
        if (material == EnumGateMaterial.CLAY_BRICK) return Component.translatable("gate.name.basic");
        return Component.translatable("gate.name", Component.translatable("gate.material." + material.tag),
                Component.translatable("gate.logic." + logic.tag));
    }
}
