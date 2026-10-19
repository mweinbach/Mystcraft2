package net.mystcraft.smoke.forge;

import net.mystcraft.smoke.SmokeCommand;
import net.mystcraft.smoke.SmokeRegionPreparation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import java.util.ArrayList;

@Mod("mystcraft_smoke")
public final class ForgeSmokeMod {
    public ForgeSmokeMod() {
        // Forge's native serializer requires the named loot-context stack, even for a test-only table.
        net.mystcraft.smoke.LoadedTreasureFixture.installDecoder((server,json)->
                net.minecraftforge.common.ForgeHooks.loadLootTable(
                        net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create(),
                        new net.minecraft.resources.ResourceLocation("mystcraft","smoke/source_treasure"),json,true,server.getLootTables()));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                SmokeCommand.register(event.getDispatcher(), (server, level) -> {
                    var results = new ArrayList<>(ForgeTravelVetoSmoke.run(server, level));
                    results.addAll(ForgeEntityVetoSmoke.run(server, level));
                    results.addAll(ForgeBlackDecayVetoSmoke.run(server, level));
                    return results;
                }));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) SmokeRegionPreparation.tick(event.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> SmokeRegionPreparation.stop(event.getServer()));
    }
}
