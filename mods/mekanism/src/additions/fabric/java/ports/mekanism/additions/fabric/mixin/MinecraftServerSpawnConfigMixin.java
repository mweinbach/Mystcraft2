/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.additions.fabric.mixin;

import com.mojang.datafixers.DataFixer;
import java.net.Proxy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.additions.fabric.FabricAdditionsSpawns;

/**
 * Biome modifications run in this constructor, before the server-starting event. Baby-mob weights are server config.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSpawnConfigMixin {

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void mekanism$loadAdditionsConfig(Thread thread, LevelStorageSource.LevelStorageAccess storage, PackRepository packs, WorldStem stem,
          Proxy proxy, DataFixer fixer, Services services, ChunkProgressListenerFactory progress, CallbackInfo ci) {
        FabricAdditionsSpawns.prepareServerConfig(storage.getLevelPath(LevelResource.ROOT).resolve("serverconfig"));
    }
}
