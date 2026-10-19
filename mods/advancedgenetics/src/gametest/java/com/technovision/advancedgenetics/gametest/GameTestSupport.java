package com.technovision.advancedgenetics.gametest;

import com.mojang.authlib.GameProfile;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import java.util.UUID;

final class GameTestSupport {

    static final String TEMPLATE = "empty";

    private GameTestSupport() { }

    /**
     * Creates a server player that is never added to the player list, so tests do not share genes or state.
     */
    static FakePlayer player(GameTestHelper helper, String name, BlockPos relativePos) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(relativePos);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }

    static FakePlayer player(GameTestHelper helper, String name, Genes... genes) {
        FakePlayer player = player(helper, name, new BlockPos(1, 2, 1));
        PlayerGeneticsComponent genetics = ComponentRegistry.getGenetics(player);
        for (Genes gene : genes) genetics.addGene(gene);
        return player;
    }

    static PlayerGeneticsComponent genetics(FakePlayer player) {
        return ComponentRegistry.getGenetics(player);
    }

    static IEnergyStorage energy(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(ForgeCapabilities.ENERGY, side)
                .orElseThrow(() -> new GameTestAssertException("Missing energy capability on " + side));
    }

    static IItemHandler items(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Missing item handler on " + side));
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
