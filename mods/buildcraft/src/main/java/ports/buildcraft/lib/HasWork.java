package ports.buildcraft.lib;

/** Upstream {@code IHasWork} ({@code TilesAPI.CAP_HAS_WORK}): machines the "machine on/off" gate triggers read. */
public interface HasWork {
    boolean hasWork();
}
