/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.additions.fabric.mixin;

import mekanism.additions.common.entity.baby.BabyType;
import mekanism.additions.common.world.modifier.BabyEntitySpawnStructureModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.world.ModifiableStructureInfo;
import ports.mekanism.lib.common.world.StructureModifier;
import ports.mekanism.lib.common.world.StructureSettingsBuilder.StructureSpawnOverrideBuilder;

/**
 * Forge's structure modifiers run before this list is chosen. Fabric's biome modifications already rewrote the biome
 * list, so a returned list that is still that biome list is left alone. A structure override is a different list, and
 * the baby-mob modifier appends to it.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorSpawnMixin {

    @Inject(method = "getMobsAt", at = @At("RETURN"), cancellable = true)
    private void mekanism$babyStructureSpawns(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos,
          CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> cir) {
        if (category != MobCategory.MONSTER) {
            return;
        }
        WeightedRandomList<MobSpawnSettings.SpawnerData> result = cir.getReturnValue();
        if (result == biome.value().getMobSettings().getMobs(category)) {
            return;
        }
        Structure matched = null;
        StructureSpawnOverride matchedOverride = null;
        for (Structure structure : structureManager.getAllStructuresAt(pos).keySet()) {
            StructureSpawnOverride override = structure.spawnOverrides().get(category);
            if (override != null && override.spawns() == result) {
                matched = structure;
                matchedOverride = override;
                break;
            }
        }
        if (matched == null) {
            return;
        }
        ModifiableStructureInfo.StructureInfo.Builder builder = new ModifiableStructureInfo.StructureInfo.Builder(matched.spawnOverrides());
        int before = matchedOverride.spawns().unwrap().size();
        for (BabyType type : BabyType.values()) {
            new BabyEntitySpawnStructureModifier(type).modify(Holder.direct(matched), StructureModifier.Phase.REMOVE, builder);
        }
        StructureSpawnOverrideBuilder monsters = builder.getStructureSettings().getSpawnOverrides(MobCategory.MONSTER);
        if (monsters == null || monsters.getSpawns().size() == before) {
            return;
        }
        cir.setReturnValue(WeightedRandomList.create(monsters.getSpawns()));
    }
}
