package ports.jurassicraft.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.jurassicraft.fabric.client.VehicleKeyDispatch;
import ports.jurassicraft.fabric.client.VehicleKeyScopes;

/**
 * Vanilla keeps one mapping per physical key, so a helicopter binding on Space or Left Ctrl would
 * never see the key (or would take it from jump or sprint). After vanilla has delivered the event,
 * also deliver it to the vehicle bindings on that key; helicopter flight then releases the vanilla
 * mapping while the pilot is holding it. Nothing is cancelled and ordinary bindings are untouched.
 */
@Mixin(KeyMapping.class)
public abstract class VehicleKeyDispatchMixin {
    @Shadow @Final private static Map<String, KeyMapping> ALL;
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;
    @Shadow private InputConstants.Key key;
    @Shadow private int clickCount;

    @Inject(method = "set", at = @At("TAIL"))
    private static void jurassicraft$setVehicleBindings(InputConstants.Key pressed, boolean held, CallbackInfo callback) {
        for (KeyMapping mapping : jurassicraft$extraRecipients(pressed)) mapping.setDown(held);
    }

    @Inject(method = "click", at = @At("TAIL"))
    private static void jurassicraft$clickVehicleBindings(InputConstants.Key pressed, CallbackInfo callback) {
        for (KeyMapping mapping : jurassicraft$extraRecipients(pressed)) ((VehicleKeyDispatchMixin) (Object) mapping).clickCount++;
    }

    private static List<KeyMapping> jurassicraft$extraRecipients(InputConstants.Key pressed) {
        List<KeyMapping> bound = new ArrayList<>(2);
        for (KeyMapping mapping : ALL.values()) {
            if (pressed.equals(((VehicleKeyDispatchMixin) (Object) mapping).key)) bound.add(mapping);
        }
        if (bound.size() < 2) return List.of();
        return VehicleKeyDispatch.extraRecipients(MAP.get(pressed), bound, VehicleKeyScopes::isScoped);
    }
}
