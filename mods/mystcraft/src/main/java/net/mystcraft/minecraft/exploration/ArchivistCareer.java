package net.mystcraft.minecraft.exploration;

import net.minecraft.world.entity.npc.Villager;
import net.mystcraft.integration.registry.ExplorationRegistry;

/** Modern native ResetProfession must not erase the source's permanent village career. */
public final class ArchivistCareer {
    public static final String INITIALIZE = "mystcraft:initialize_archivist_career";
    private ArchivistCareer() {}
    public static void initialize(Villager villager) {
        if (villager.level.isClientSide || !villager.getTags().contains(INITIALIZE)) return;
        var profession = ExplorationRegistry.ARCHIVIST.get();
        if (net.minecraft.core.Registry.VILLAGER_PROFESSION.getKey(profession) == null) return;
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        villager.setVillagerXp(Math.max(1, villager.getVillagerXp()));
        villager.removeTag(INITIALIZE);
    }
}
