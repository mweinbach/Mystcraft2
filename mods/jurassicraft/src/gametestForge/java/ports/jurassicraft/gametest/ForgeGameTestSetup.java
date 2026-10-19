package ports.jurassicraft.gametest;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID)
public final class ForgeGameTestSetup {
    private ForgeGameTestSetup() {}

    @SubscribeEvent public static void serverStarted(ServerStartedEvent event) { GameTestEntityGate.install(); }
}
