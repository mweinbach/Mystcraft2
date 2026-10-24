package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.AgriApi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;

public class EmcGameTests {

	@GameTest(template = TEMPLATE)
	public static void everyItemHasAnEmcDecision(GameTestHelper helper) {
		List<String> failures = EmcAudit.run(helper.getLevel().getServer(), AgriApi.MOD_ID);
		if (!failures.isEmpty()) helper.fail(EmcAudit.summary(failures));
		helper.succeed();
	}

}
