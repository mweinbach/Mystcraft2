package com.technovision.advancedgenetics.component;

import com.technovision.advancedgenetics.api.component.EntityGeneticsComponent;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerGeneticsComponent implements EntityGeneticsComponent {

    private final Map<String, Genes> genes = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Player player;
    private int tickCounter;
    private long totalSeconds;

    public PlayerGeneticsComponent(Player player) {
        this.player = player;
        this.tickCounter = 0;
        this.totalSeconds = 0;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        genes.clear();
        CompoundTag genesTag = tag.getCompound("genes");
        for (String geneName : genesTag.getAllKeys()) {
            Genes gene = Genes.byName(geneName);
            if (gene != null) genes.put(geneName, gene);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag genesTag = new CompoundTag();
        for (Genes gene : genes.values()) {
            genesTag.putString(gene.toString(), gene.getName());
        }
        tag.put("genes", genesTag);
        return tag;
    }

    /**
     * Replaces this player's genes with another player's, for respawn and dimension changes.
     */
    public void copyFrom(EntityGeneticsComponent other) {
        deserializeNBT(other.serializeNBT());
    }

    @Override
    public void serverTick() {
        // Handler timers
        tickCounter++;
        if (tickCounter < 20) return;
        totalSeconds++;

        tickCounter = 0;
        if (totalSeconds == Long.MAX_VALUE) totalSeconds = 0;

        // Potion effect genes
        if (totalSeconds % 4 == 0) {
            checkFoodStatus();
            checkFlightStatus();
            applyPotionEffects();
        }

        // Lay egg gene (every 5 min)
        if (totalSeconds % 300 == 0) {
            if (hasGene(Genes.LAY_EGG)) {
                player.spawnAtLocation(new ItemStack(Items.EGG));
                player.playSound(SoundEvents.CHICKEN_EGG, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Apply permanent potion effects based on active genes.
     */
    private void applyPotionEffects() {
        applyEffect(Genes.RESISTANCE, MobEffects.DAMAGE_RESISTANCE);
        applyEffect(Genes.HASTE, MobEffects.DIG_SPEED);
        applyEffect(Genes.SPEED, MobEffects.MOVEMENT_SPEED);
        applyEffect(Genes.REGENERATION, MobEffects.REGENERATION);
        applyEffect(Genes.STRENGTH, MobEffects.DAMAGE_BOOST);
        applyEffect(Genes.FIREPROOF, MobEffects.FIRE_RESISTANCE);
        applyEffect(Genes.NIGHT_VISION, MobEffects.NIGHT_VISION);
        applyEffect(Genes.JUMP_BOOST, MobEffects.JUMP);
        applyEffect(Genes.WATER_BREATHING, MobEffects.WATER_BREATHING);
        applyEffect(Genes.INVISIBILITY, MobEffects.INVISIBILITY);
        applyEffect(Genes.LUCK, MobEffects.LUCK);
    }

    private void applyEffect(Genes gene, MobEffect effect) {
        if (hasGene(gene)) {
            player.forceAddEffect(new MobEffectInstance(effect, 20*6, 0, false, false, false), player);
        }
    }

    /**
     * Checks if a user is at half hunger and uses the
     * "no-hunger" gene to prevent further loss.
     */
    private void checkFoodStatus() {
        if (player.getFoodData().getFoodLevel() > 10) return;
        if (hasGene(Genes.NO_HUNGER)) {
            player.getFoodData().eat(1, 0.0f);
        }
    }

    /**
     * Checks if a user has the flight gene and enables/disables flight.
     */
    private void checkFlightStatus() {
        if (player.isCreative()) return;
        if (hasGene(Genes.FLIGHT)) {
            player.getAbilities().mayfly = true;
        } else {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
    }

    @Override
    public int geneCount() {
        return genes.size();
    }

    @Override
    public List<Genes> getGenes() {
        return genes.values().stream().toList();
    }

    @Override
    public Map<String, Genes> getGenesMap() {
        return genes;
    }

    @Override
    public boolean hasGene(Genes gene) {
        if (!gene.isEnabled()) return false;
        return genes.containsKey(gene.toString());
    }

    @Override
    public void addGene(Genes gene) {
        genes.put(gene.toString(), gene);
        sync();
    }

    @Override
    public void addGenes(List<Genes> genesList) {
        for (Genes gene : genesList) {
            genes.put(gene.toString(), gene);
        }
        sync();
    }

    @Override
    public void removeGene(Genes gene) {
        genes.remove(gene.toString());
        sync();
    }

    @Override
    public void removeAllGenes() {
        genes.clear();
        sync();
    }

    @Override
    public void addCooldown(String key, long seconds) {
        cooldowns.put(key, System.currentTimeMillis() + (1000 * seconds));
    }

    @Override
    public boolean isOnCooldown(String key) {
        Long cooldown = cooldowns.get(key);
        if (cooldown == null) return false;
        return (System.currentTimeMillis() < cooldown);
    }

    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.syncGenetics(serverPlayer);
        }
    }
}
