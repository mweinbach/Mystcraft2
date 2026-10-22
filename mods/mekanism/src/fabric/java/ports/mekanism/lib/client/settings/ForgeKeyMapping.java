/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import ports.mekanism.lib.client.extensions.IForgeKeyMapping;

/**
 * Supplies Forge's {@code KeyMapping} constructor with a conflict context and modifier. The Fabric build rewrites
 * shared subclasses of {@code KeyMapping} to extend this class. The modifier is stored on the mapping itself, so a
 * later rebinding replaces it.
 */
public class ForgeKeyMapping extends KeyMapping {

    public ForgeKeyMapping(String description, int keyCode, String category) {
        this(description, KeyConflictContext.UNIVERSAL, KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(keyCode), category);
    }

    public ForgeKeyMapping(String description, IKeyConflictContext keyConflictContext, KeyModifier keyModifier, InputConstants.Key key, String category) {
        super(description, key.getType(), key.getValue(), category);
        IForgeKeyMapping self = (IForgeKeyMapping) this;
        self.setKeyConflictContext(keyConflictContext);
        self.setDefaultKeyModifier(keyModifier);
        self.setKeyModifierAndCode(keyModifier, key);
    }
}
