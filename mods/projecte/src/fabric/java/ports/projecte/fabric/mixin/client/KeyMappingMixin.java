/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.client.extensions.IForgeKeyMapping;
import ports.projecte.lib.client.settings.IKeyConflictContext;
import ports.projecte.lib.client.settings.KeyConflictContext;
import ports.projecte.lib.client.settings.KeyModifier;

/**
 * Gives every key mapping a modifier and a conflict context, and delivers a press to each active mapping bound to that
 * key. A release clears every mapping on the key, including one whose modifier is no longer held, so a chord cannot
 * stay down. Forge 43.5.0 only releases the mappings that are still active.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IForgeKeyMapping {

    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    @Final
    private static Map<String, Integer> CATEGORY_SORT_ORDER;

    @Shadow
    private InputConstants.Key key;

    @Shadow
    private String name;

    @Shadow
    private String category;

    @Shadow
    public abstract void setKey(InputConstants.Key key);

    @Unique
    private KeyModifier projecte$keyModifier = KeyModifier.NONE;

    @Unique
    private KeyModifier projecte$keyModifierDefault = KeyModifier.NONE;

    @Unique
    private IKeyConflictContext projecte$keyConflictContext = KeyConflictContext.UNIVERSAL;

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void projecte$setActiveBindings(InputConstants.Key key, boolean held, CallbackInfo ci) {
        for (KeyMapping mapping : ALL.values()) {
            IForgeKeyMapping forge = (IForgeKeyMapping) mapping;
            if (!key.equals(forge.getKey())) {
                continue;
            }
            if (!held || forge.isConflictContextAndModifierActive()) {
                mapping.setDown(held);
            }
        }
        ci.cancel();
    }

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void projecte$requireContext(CallbackInfoReturnable<Boolean> cir) {
        if (!isConflictContextAndModifierActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "saveString", at = @At("RETURN"), cancellable = true)
    private void projecte$saveModifier(CallbackInfoReturnable<String> cir) {
        if (projecte$keyModifier != KeyModifier.NONE) {
            cir.setReturnValue(cir.getReturnValue() + ":" + projecte$keyModifier.name());
        } else if (projecte$keyModifierDefault != KeyModifier.NONE) {
            // A bare default key is a legacy options entry and reloads the default modifier. Write :NONE when the
            // player cleared that modifier so the next load keeps the key unmodified.
            cir.setReturnValue(cir.getReturnValue() + ":NONE");
        }
    }

    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void projecte$modifierName(CallbackInfoReturnable<Component> cir) {
        Component keyName = cir.getReturnValue();
        cir.setReturnValue(projecte$keyModifier.getCombinedName(key, () -> keyName));
    }

    @Inject(method = "isDefault", at = @At("RETURN"), cancellable = true)
    private void projecte$defaultModifier(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cir.getReturnValueZ() && projecte$keyModifier == projecte$keyModifierDefault);
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void projecte$same(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        IForgeKeyMapping them = (IForgeKeyMapping) other;
        if (!projecte$keyConflictContext.conflicts(them.getKeyConflictContext()) && !them.getKeyConflictContext().conflicts(projecte$keyConflictContext)) {
            return;
        }
        KeyModifier otherModifier = them.getKeyModifier();
        if (projecte$keyModifier.matches(them.getKey()) || otherModifier.matches(key)) {
            cir.setReturnValue(true);
        } else if (key.equals(them.getKey())) {
            cir.setReturnValue(projecte$keyModifier == otherModifier
                  || (projecte$keyConflictContext.conflicts(KeyConflictContext.IN_GAME)
                  && (projecte$keyModifier == KeyModifier.NONE || otherModifier == KeyModifier.NONE)));
        }
    }

    @Inject(method = "compareTo(Lnet/minecraft/client/KeyMapping;)I", at = @At("HEAD"), cancellable = true)
    private void projecte$compare(KeyMapping other, CallbackInfoReturnable<Integer> cir) {
        if (category.equals(other.getCategory())) {
            cir.setReturnValue(I18n.get(name).compareTo(I18n.get(other.getName())));
            return;
        }
        Integer ours = CATEGORY_SORT_ORDER.get(category);
        Integer theirs = CATEGORY_SORT_ORDER.get(other.getCategory());
        if (ours == null && theirs != null) {
            cir.setReturnValue(1);
        } else if (ours != null && theirs == null) {
            cir.setReturnValue(-1);
        } else if (ours == null) {
            cir.setReturnValue(I18n.get(category).compareTo(I18n.get(other.getCategory())));
        } else {
            cir.setReturnValue(ours.compareTo(theirs));
        }
    }

    @Override
    public InputConstants.Key getKey() {
        return key;
    }

    @Override
    public void setKeyConflictContext(IKeyConflictContext keyConflictContext) {
        this.projecte$keyConflictContext = keyConflictContext;
    }

    @Override
    public IKeyConflictContext getKeyConflictContext() {
        return projecte$keyConflictContext;
    }

    @Override
    public KeyModifier getDefaultKeyModifier() {
        return projecte$keyModifierDefault;
    }

    @Override
    public void setDefaultKeyModifier(KeyModifier keyModifier) {
        this.projecte$keyModifierDefault = keyModifier;
    }

    @Override
    public KeyModifier getKeyModifier() {
        return projecte$keyModifier;
    }

    @Override
    public void setKeyModifierAndCode(KeyModifier keyModifier, InputConstants.Key keyCode) {
        if (keyModifier.matches(keyCode)) {
            keyModifier = KeyModifier.NONE;
        }
        setKey(keyCode);
        this.projecte$keyModifier = keyModifier;
    }
}
