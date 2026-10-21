package ports.buildcraft.statement;

import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code IGate}: a gate on one face of a pipe. */
public interface Gate extends SidedStatementContainer {
    PipeBlockEntity getPipe();
}
