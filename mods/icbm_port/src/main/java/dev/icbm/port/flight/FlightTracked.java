package dev.icbm.port.flight;

/** Generic contract for payload and held-item entities transported by the same ledger. */
public interface FlightTracked {
  StagedFlight flightPlan();
}
