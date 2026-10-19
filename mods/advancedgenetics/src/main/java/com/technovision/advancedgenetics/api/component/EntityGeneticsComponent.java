package com.technovision.advancedgenetics.api.component;

import com.technovision.advancedgenetics.api.genetics.Genes;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;

public interface EntityGeneticsComponent {

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag nbt);

    int geneCount();

    List<Genes> getGenes();

    Map<String, Genes> getGenesMap();

    boolean hasGene(Genes gene);

    void addGene(Genes gene);

    void addGenes(List<Genes> genes);

    void removeGene(Genes gene);

    void removeAllGenes();

    void addCooldown(String key, long seconds);

    boolean isOnCooldown(String key);

    void serverTick();
}
