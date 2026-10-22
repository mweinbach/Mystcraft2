/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

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
import ports.mekanism.lib.client.extensions.IForgeKeyMapping;
import ports.mekanism.lib.client.settings.IKeyConflictContext;
import ports.mekanism.lib.client.settings.KeyConflictContext;
import ports.mekanism.lib.client.settings.KeyModifier;

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
    private KeyModifier mekanism$keyModifier = KeyModifier.NONE;

    @Unique
    private KeyModifier mekanism$keyModifierDefault = KeyModifier.NONE;

    @Unique
    private IKeyConflictContext mekanism$keyConflictContext = KeyConflictContext.UNIVERSAL;

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void mekanism$setActiveBindings(InputConstants.Key key, boolean held, CallbackInfo ci) {
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
    private void mekanism$requireContext(CallbackInfoReturnable<Boolean> cir) {
        if (!isConflictContextAndModifierActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "saveString", at = @At("RETURN"), cancellable = true)
    private void mekanism$saveModifier(CallbackInfoReturnable<String> cir) {
        if (mekanism$keyModifier != KeyModifier.NONE) {
            cir.setReturnValue(cir.getReturnValue() + ":" + mekanism$keyModifier.name());
        }
    }

    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void mekanism$modifierName(CallbackInfoReturnable<Component> cir) {
        Component keyName = cir.getReturnValue();
        cir.setReturnValue(mekanism$keyModifier.getCombinedName(key, () -> keyName));
    }

    @Inject(method = "isDefault", at = @At("RETURN"), cancellable = true)
    private void mekanism$defaultModifier(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cir.getReturnValueZ() && mekanism$keyModifier == mekanism$keyModifierDefault);
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void mekanism$same(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        IForgeKeyMapping them = (IForgeKeyMapping) other;
        if (!mekanism$keyConflictContext.conflicts(them.getKeyConflictContext()) && !them.getKeyConflictContext().conflicts(mekanism$keyConflictContext)) {
            return;
        }
        KeyModifier otherModifier = them.getKeyModifier();
        if (mekanism$keyModifier.matches(them.getKey()) || otherModifier.matches(key)) {
            cir.setReturnValue(true);
        } else if (key.equals(them.getKey())) {
            cir.setReturnValue(mekanism$keyModifier == otherModifier
                  || (mekanism$keyConflictContext.conflicts(KeyConflictContext.IN_GAME)
                  && (mekanism$keyModifier == KeyModifier.NONE || otherModifier == KeyModifier.NONE)));
        }
    }

    @Inject(method = "compareTo(Lnet/minecraft/client/KeyMapping;)I", at = @At("HEAD"), cancellable = true)
    private void mekanism$compare(KeyMapping other, CallbackInfoReturnable<Integer> cir) {
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
        this.mekanism$keyConflictContext = keyConflictContext;
    }

    @Override
    public IKeyConflictContext getKeyConflictContext() {
        return mekanism$keyConflictContext;
    }

    @Override
    public KeyModifier getDefaultKeyModifier() {
        return mekanism$keyModifierDefault;
    }

    @Override
    public void setDefaultKeyModifier(KeyModifier keyModifier) {
        this.mekanism$keyModifierDefault = keyModifier;
    }

    @Override
    public KeyModifier getKeyModifier() {
        return mekanism$keyModifier;
    }

    @Override
    public void setKeyModifierAndCode(KeyModifier keyModifier, InputConstants.Key keyCode) {
        if (keyModifier.matches(keyCode)) {
            keyModifier = KeyModifier.NONE;
        }
        setKey(keyCode);
        this.mekanism$keyModifier = keyModifier;
    }
}
