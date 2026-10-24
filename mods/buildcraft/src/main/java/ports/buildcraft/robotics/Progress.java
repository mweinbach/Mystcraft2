package ports.buildcraft.robotics;

/** How a robot's multi-tick step went this tick. Written for this port. */
public enum Progress {
    /** Still under way; call again next tick. */
    WORKING,
    /** Finished. */
    DONE,
    /** Cannot be done; the caller should pick something else. */
    FAILED
}
