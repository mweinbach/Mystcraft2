package ports.jurassicraft.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.jurassicraft.fabric.PersistentDataHolder;

/** Server-only entity data equivalent to Forge's persistent data compound. */
@Mixin(Entity.class)
public abstract class EntityPersistentDataMixin implements PersistentDataHolder {
    @Unique private static final String KEY = "JurassiCraftData";
    @Unique private CompoundTag jurassicraft$data;

    @Override public CompoundTag jurassicraft$persistentData() {
        if (jurassicraft$data == null) jurassicraft$data = new CompoundTag();
        return jurassicraft$data;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void jurassicraft$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> result) {
        if (jurassicraft$data != null && !jurassicraft$data.isEmpty()) tag.put(KEY, jurassicraft$data.copy());
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void jurassicraft$load(CompoundTag tag, CallbackInfo callback) {
        jurassicraft$data = tag.contains(KEY, Tag.TAG_COMPOUND) ? tag.getCompound(KEY).copy() : null;
    }
}
