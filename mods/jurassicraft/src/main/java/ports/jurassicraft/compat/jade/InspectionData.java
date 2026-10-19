package ports.jurassicraft.compat.jade;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import ports.jurassicraft.hatching.HatcheryRegistry;

/** Server-side snapshots containing only the information displayed by the original WAILA addon. */
public final class InspectionData {
    private InspectionData() {}
    public static CompoundTag dinosaur(DinosaurEntity dinosaur, @Nullable String ownerName) {
        CompoundTag result = new CompoundTag();
        result.putBoolean("Male", dinosaur.isMale());
        result.putString("Order", dinosaur.getOrder().name().toLowerCase(Locale.ROOT));
        if (dinosaur.getOwnerUUID() != null) {
            result.putUUID("Owner", dinosaur.getOwnerUUID());
            if (ownerName != null && !ownerName.isBlank()) result.putString("OwnerName", ownerName);
        }
        return result;
    }
    public static CompoundTag feeder(FeederBlockEntity feeder) {
        CompoundTag result = new CompoundTag();
        feeder.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(items -> {
            int meat = 0, plants = 0;
            for (int i = 0; i < Math.min(items.getSlots(), FeederBlockEntity.SLOT_COUNT); i++) {
                int count = items.getStackInSlot(i).getCount();
                if (i < FeederBlockEntity.MEAT_SLOTS) meat += count; else plants += count;
            }
            result.putInt("Meat", Math.max(0, Math.min(9 * 64, meat)));
            result.putInt("Plants", Math.max(0, Math.min(9 * 64, plants)));
        });
        return result;
    }
    public static CompoundTag incubator(HatcheryBlockEntity incubator) {
        CompoundTag result = new CompoundTag();
        if (!incubator.getBlockState().is(HatcheryRegistry.block(HatcheryRegistry.Machine.INCUBATOR))) return result;
        CompoundTag state = incubator.saveWithoutMetadata();
        int[] progress = state.getIntArray("Progress"), temperature = state.getIntArray("Temperature");
        ListTag eggs = new ListTag();
        for (int slot = 0; slot < 5; slot++) {
            ItemStack stack = incubator.getItem(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.putInt("Progress", slot < progress.length ? Math.max(0, Math.min(HatcheryRegistry.Machine.INCUBATOR.ticks, progress[slot])) : 0);
            entry.putInt("Temperature", slot < temperature.length ? Math.max(0, Math.min(100, temperature[slot])) : 0);
            ItemStack one = stack.copy(); one.setCount(1);
            entry.put("Egg", one.save(new CompoundTag()));
            eggs.add(entry);
        }
        result.put("Eggs", eggs);
        return result;
    }
}
