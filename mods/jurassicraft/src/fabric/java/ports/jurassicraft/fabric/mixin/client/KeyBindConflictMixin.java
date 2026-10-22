package ports.jurassicraft.fabric.mixin.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.jurassicraft.fabric.client.VehicleKeyScopes;

/**
 * Vanilla paints every shared physical key red. A binding that works only while riding does not
 * take jump, sprint or the hotbar, so it is not shown as a conflict with those in-game keys.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindConflictMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIIIIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"))
    private boolean jurassicraft$vehicleKeysDoNotConflict(KeyMapping self, KeyMapping other) {
        if (VehicleKeyScopes.separated(self, other)) return false;
        return self.same(other);
    }
}
