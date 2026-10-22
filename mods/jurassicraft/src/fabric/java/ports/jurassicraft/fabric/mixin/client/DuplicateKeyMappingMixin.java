package ports.jurassicraft.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla keeps one mapping per physical key, so a second Space or Left Ctrl binding would steal
 * jump or sprint. Deliver each event to every mapping bound to that key; helicopter flight then
 * releases the vanilla mapping while the pilot is holding it.
 */
@Mixin(KeyMapping.class)
public abstract class DuplicateKeyMappingMixin {
    @Shadow private static Map<String, KeyMapping> ALL;
    @Shadow private InputConstants.Key key;
    @Shadow private int clickCount;

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void jurassicraft$setEveryBinding(InputConstants.Key pressed, boolean held, CallbackInfo callback) {
        for (KeyMapping mapping : ALL.values()) {
            if (pressed.equals(bound(mapping))) mapping.setDown(held);
        }
        callback.cancel();
    }

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void jurassicraft$clickEveryBinding(InputConstants.Key pressed, CallbackInfo callback) {
        for (KeyMapping mapping : ALL.values()) {
            if (pressed.equals(bound(mapping))) ((DuplicateKeyMappingMixin) (Object) mapping).clickCount++;
        }
        callback.cancel();
    }

    private static InputConstants.Key bound(KeyMapping mapping) {
        return ((DuplicateKeyMappingMixin) (Object) mapping).key;
    }
}
