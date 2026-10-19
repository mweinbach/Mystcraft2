package net.mystcraft.fabric.mixin;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(LootTables.class)
abstract class LegacyTreasureLootMixin {
    @ModifyVariable(method="apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Map<ResourceLocation,JsonElement> mystcraft$legacyHook(Map<ResourceLocation,JsonElement> tables){return net.mystcraft.minecraft.exploration.LegacyTreasureHooks.apply(tables);}
}
