package ports.gridwright.gametest.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import ports.gridwright.gametest.TestPlatform;
import ports.gridwright.gametest.TransferTestPlatform;
import ports.gridwright.gametest.TestProtection;

public class FabricTestSetup implements ModInitializer {

    @Override
    public void onInitialize() {
        TestPlatform.Holder.install(new FabricTestPlatform());
        FabricTestPlatform.registerEnergySink();
        FabricTestPlatform.registerClaims();
        TransferTestPlatform.Holder.install(new FabricTransferTestPlatform());
        FabricTransferTestPlatform.register();
        // A stand-in claim mod: refuses breaks and right-clicks at positions the tests protect.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !TestProtection.isProtected(pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                TestProtection.isUseDenied(hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
