/*
 * ProjectE 1.19.2 port - Fabric replacement for an upstream class.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.integration;

import java.util.function.Supplier;
import moze_intel.projecte.PECore;
import moze_intel.projecte.capability.ItemCapability;
import moze_intel.projecte.integration.curios.CurioItemCapability;
import moze_intel.projecte.integration.curios.CuriosIntegration;
import net.minecraft.resources.ResourceLocation;
import ports.projecte.lib.fml.ModList;
import ports.projecte.lib.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * The Fabric counterpart of upstream's {@code IntegrationHelper}. Curios has no Fabric build; Trinkets, which Tekxit 4
 * ships, takes its place under the same names, so the shared item code that asks for the "curio" capability gets a
 * Trinkets slot instead. The One Probe has no Fabric build and is not offered.
 */
public class IntegrationHelper {

	public static final String CURIO_MODID = "trinkets";
	public static final String TOP_MODID = "theoneprobe";

	//Double supplier to make sure it does not resolve early
	public static final Supplier<Supplier<ItemCapability<?>>> CURIO_CAP_SUPPLIER = () -> CurioItemCapability::new;

	public static final ResourceLocation CURIOS_KLEIN_STAR = PECore.rl("curios/empty_klein_star");

	public static void sendIMCMessages(InterModEnqueueEvent event) {
		if (ModList.get().isLoaded(CURIO_MODID)) {
			CuriosIntegration.sendIMC(event);
		}
	}
}
