package com.technovision.advancedgenetics.common.goal;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Flee from players that have a certain gene. Upstream picked the nearest player and then
 * checked the gene; here the nearest player that has the gene is chosen.
 */
public class FleePlayerGoal extends AvoidEntityGoal<Player> {

    public FleePlayerGoal(PathfinderMob mob, Genes requiredGene, float distance, double slowSpeed, double fastSpeed) {
        super(mob, Player.class,
                entity -> entity instanceof Player player && ComponentRegistry.getGenetics(player).hasGene(requiredGene),
                distance, slowSpeed, fastSpeed, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }
}
