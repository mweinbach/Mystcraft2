/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Supplies Forge's {@code KeyMapping} constructor with a conflict context and modifier. The Fabric build rewrites
 * ProjectE's {@code new KeyMapping(...)} calls to this class.
 * <p>
 * Forge lets several mappings share a key and picks between them by the held modifier (ProjectE binds Shift+X to the
 * helmet and X to the boots). Vanilla delivers a key to only one mapping, and patching vanilla's key handling would
 * fight the other Forge API layers in a pack (Mekanism's port patches the same methods). So each mapping polls its own
 * key once per client tick ({@link #tickAll()}) and counts its presses itself; vanilla's own press count is drained and
 * ignored. The modifier is not editable in the vanilla controls screen; the default one applies.
 */
public class ForgeKeyMapping extends KeyMapping {

    private static final List<ForgeKeyMapping> ALL = new ArrayList<>();

    private final IKeyConflictContext keyConflictContext;
    private final KeyModifier keyModifier;
    private boolean projecteDown;
    private int projecteClicks;

    public ForgeKeyMapping(String description, int keyCode, String category) {
        this(description, KeyConflictContext.UNIVERSAL, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(keyCode), category);
    }

    public ForgeKeyMapping(String description, IKeyConflictContext keyConflictContext, KeyModifier keyModifier, InputConstants.Type inputType, int keyCode,
          String category) {
        this(description, keyConflictContext, keyModifier, inputType.getOrCreate(keyCode), category);
    }

    public ForgeKeyMapping(String description, IKeyConflictContext keyConflictContext, KeyModifier keyModifier, InputConstants.Key key, String category) {
        super(description, key.getType(), key.getValue(), category);
        this.keyConflictContext = keyConflictContext;
        this.keyModifier = keyModifier;
        ALL.add(this);
    }

    public IKeyConflictContext getKeyConflictContext() {
        return keyConflictContext;
    }

    public KeyModifier getKeyModifier() {
        return keyModifier;
    }

    /**
     * Called at the start of every client tick, before the mods' tick listeners read {@link #consumeClick()}.
     */
    public static void tickAll() {
        for (ForgeKeyMapping mapping : ALL) {
            mapping.tick();
        }
    }

    private void tick() {
        while (super.consumeClick()) {
            // Vanilla's count would only be stale; this mapping counts its own presses.
        }
        boolean down = isPhysicallyDown() && isActiveNow();
        if (down && !projecteDown) {
            projecteClicks++;
        }
        projecteDown = down;
    }

    private boolean isPhysicallyDown() {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(this);
        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> super.isDown();
        };
    }

    /**
     * Forge's lookup: a mapping whose modifier is held wins over an unmodified mapping on the same key.
     */
    private boolean isActiveNow() {
        if (!keyConflictContext.isActive() || !keyModifier.isActive(keyConflictContext)) {
            return false;
        }
        if (keyModifier == KeyModifier.NONE) {
            InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(this);
            for (ForgeKeyMapping other : ALL) {
                if (other != this && other.keyModifier != KeyModifier.NONE && key.equals(KeyBindingHelper.getBoundKeyOf(other))
                    && other.keyConflictContext.isActive() && other.keyModifier.isActive(other.keyConflictContext)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isDown() {
        return projecteDown;
    }

    @Override
    public boolean consumeClick() {
        if (projecteClicks == 0) {
            return false;
        }
        projecteClicks--;
        return true;
    }

    @Override
    public Component getTranslatedKeyMessage() {
        return keyModifier.getCombinedName(KeyBindingHelper.getBoundKeyOf(this), super::getTranslatedKeyMessage);
    }
}
