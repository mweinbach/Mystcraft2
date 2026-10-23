/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.gametest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.metalrender.MetalRenderMod;
import ports.metalrender.terrain.PackedLayer;
import ports.metalrender.terrain.SectionSlot;
import ports.metalrender.terrain.SectionStore;
import ports.metalrender.terrain.TerrainLayer;
import ports.metalrender.terrain.TerrainVertexPacker;

/**
 * Dedicated-server checks: MetalRender loads on a server without touching client or native code,
 * and its platform-independent terrain bookkeeping works inside the game JVM.
 */
public final class MetalRenderGameTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void loadsOnDedicatedServerWithoutClientCode(final GameTestHelper helper) {
        check(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER, "expected a dedicated server");
        check(FabricLoader.getInstance().isModLoaded(MetalRenderMod.MOD_ID), "metalrender is not loaded");
        check(!MetalRenderMod.isClientInitialized(), "the client entrypoint ran on a dedicated server");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void terrainBookkeepingRunsServerSide(final GameTestHelper helper) {
        final ByteBuffer quad = ByteBuffer.allocate(4 * TerrainVertexPacker.SOURCE_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        final float[][] corners = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        for (int v = 0; v < 4; v++) {
            quad.putFloat(v * 32, corners[v][0]);
            quad.putFloat(v * 32 + 4, corners[v][1]);
            quad.putFloat(v * 32 + 8, corners[v][2]);
            quad.putInt(v * 32 + 12, -1);
        }
        final PackedLayer packed = TerrainVertexPacker.pack(quad, 4, TerrainLayer.SOLID.mode);
        final SectionStore store = new SectionStore(new SectionStore.HeapArena(1024, 4, 16));
        final SectionSlot slot = store.allocate(16, 64, -32, null);
        check(slot != null && store.applyLayer(slot, TerrainLayer.SOLID, packed), "could not store a section");
        check(store.sectionHighWater() == 1 && slot.quads(TerrainLayer.SOLID) == 1, "section bookkeeping is wrong");
        store.release(slot);
        check(store.residentSections() == 0 && store.usedVertices() == 0, "release leaked arena space");
        helper.succeed();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
