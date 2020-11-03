package tech.pegasys.teku.util.config;

import java.util.Objects;

public enum ValidatorPerformanceTrackingMode {
  LOGGING,
  METRICS,
  ALL;

  static ValidatorPerformanceTrackingMode fromString(final String value) {
    final String normalizedValue = value.trim().toUpperCase();
    for (ValidatorPerformanceTrackingMode mode : ValidatorPerformanceTrackingMode.values()) {
      if (Objects.equals(mode.name(), normalizedValue)) {
        return mode;
      }
    }
    throw new IllegalArgumentException("Unknown value supplied: " + value);
  }
}
