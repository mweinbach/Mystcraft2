/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A modifier key that must be held for a key mapping to trigger.
 */
public enum KeyModifier {
    CONTROL("Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) {
        @Override
        protected boolean isHeld() {
            return Screen.hasControlDown();
        }
    },
    SHIFT("Shift", GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT) {
        @Override
        protected boolean isHeld() {
            return Screen.hasShiftDown();
        }
    },
    ALT("Alt", GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT) {
        @Override
        protected boolean isHeld() {
            return Screen.hasAltDown();
        }
    },
    NONE(null) {
        @Override
        protected boolean isHeld() {
            return false;
        }

        @Override
        public boolean matches(InputConstants.Key key) {
            return false;
        }

        @Override
        public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
            if (conflictContext != null && !conflictContext.conflicts(KeyConflictContext.IN_GAME)) {
                //Outside the game context a held modifier must not block the key
                return true;
            }
            for (KeyModifier modifier : MODIFIER_VALUES) {
                if (modifier.isActive(conflictContext)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
            return defaultLogic.get();
        }
    };

    public static final KeyModifier[] MODIFIER_VALUES = {SHIFT, CONTROL, ALT};

    @Nullable
    private final String displayName;
    private final int[] keyCodes;

    KeyModifier(@Nullable String displayName, int... keyCodes) {
        this.displayName = displayName;
        this.keyCodes = keyCodes;
    }

    protected abstract boolean isHeld();

    public boolean matches(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYSYM) {
            for (int keyCode : keyCodes) {
                if (key.getValue() == keyCode) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
        return isHeld();
    }

    public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
        return Component.literal(displayName + " + ").append(defaultLogic.get());
    }

    public static KeyModifier getActiveModifier() {
        for (KeyModifier modifier : MODIFIER_VALUES) {
            if (modifier.isActive(null)) {
                return modifier;
            }
        }
        return NONE;
    }

    public static boolean isKeyCodeModifier(InputConstants.Key key) {
        for (KeyModifier modifier : MODIFIER_VALUES) {
            if (modifier.matches(key)) {
                return true;
            }
        }
        return false;
    }

    public static KeyModifier valueFromString(String stringValue) {
        try {
            return valueOf(stringValue);
        } catch (IllegalArgumentException | NullPointerException e) {
            return NONE;
        }
    }
}
