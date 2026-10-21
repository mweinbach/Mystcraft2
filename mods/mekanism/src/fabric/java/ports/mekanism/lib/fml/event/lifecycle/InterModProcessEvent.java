/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml.event.lifecycle;

import java.util.function.Predicate;
import java.util.stream.Stream;
import ports.mekanism.lib.fml.InterModComms;
import ports.mekanism.lib.fml.InterModComms.IMCMessage;
import ports.mekanism.lib.fml.ModContainer;

public class InterModProcessEvent extends ParallelDispatchEvent {

    public InterModProcessEvent(ModContainer container) {
        super(container);
    }

    public Stream<IMCMessage> getIMCStream() {
        return InterModComms.getMessages(getContainer().getModId());
    }

    public Stream<IMCMessage> getIMCStream(Predicate<String> methodFilter) {
        return InterModComms.getMessages(getContainer().getModId(), methodFilter);
    }
}
