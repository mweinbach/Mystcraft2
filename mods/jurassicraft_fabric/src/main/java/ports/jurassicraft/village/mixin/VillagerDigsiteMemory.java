package ports.jurassicraft.village.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.jurassicraft.village.PaleontologistMemory;

@Mixin(Villager.class)
public abstract class VillagerDigsiteMemory implements PaleontologistMemory {
    @Unique private BlockPos jurassicraft$anchor;
    @Override public BlockPos jurassicraft$digsiteAnchor() {
        if (jurassicraft$anchor == null) jurassicraft$anchor = ((Villager) (Object) this).blockPosition();
        return jurassicraft$anchor;
    }
    @Inject(method="readAdditionalSaveData", at=@At("TAIL"))
    private void jurassicraft$readDigsite(CompoundTag tag, CallbackInfo ci) {
        jurassicraft$anchor = tag.contains("JurassicraftDigsiteOrigin", 4) ? BlockPos.of(tag.getLong("JurassicraftDigsiteOrigin")) : null;
    }
    @Inject(method="addAdditionalSaveData", at=@At("TAIL"))
    private void jurassicraft$saveDigsite(CompoundTag tag, CallbackInfo ci) {
        if (jurassicraft$anchor != null) tag.putLong("JurassicraftDigsiteOrigin", jurassicraft$anchor.asLong());
    }
}
