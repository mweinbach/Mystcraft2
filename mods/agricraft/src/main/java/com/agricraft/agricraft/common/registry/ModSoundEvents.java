package com.agricraft.agricraft.common.registry;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.PlatformRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSoundEvents {

	public static final PlatformRegistry<SoundEvent> SOUND_EVENTS = Platform.get().createRegistry(Registry.SOUND_EVENT, AgriApi.MOD_ID);

	public static final PlatformRegistry.Entry<SoundEvent> VALVE = SOUND_EVENTS.register("valve", () -> new SoundEvent(new ResourceLocation(AgriApi.MOD_ID, "valve")));

}
