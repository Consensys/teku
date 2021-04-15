package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecConfigMerge extends DelegatingSpecConfig {

  // Fork
  private final Bytes4 mergeForkVersion;
  private final UInt64 mergeForkSlot;

  // Transition
  private final long transitionTotalDifficulty;

  // Execution
  private final int maxBytesPerOpaqueTransaction;
  private final int maxApplicationTransactions;

  public SpecConfigMerge(
      SpecConfig specConfig,
      Bytes4 mergeForkVersion,
      UInt64 mergeForkSlot,
      long transitionTotalDifficulty,
      int maxBytesPerOpaqueTransaction,
      int maxApplicationTransactions) {
    super(specConfig);
    this.mergeForkVersion = mergeForkVersion;
    this.mergeForkSlot = mergeForkSlot;
    this.transitionTotalDifficulty = transitionTotalDifficulty;
    this.maxBytesPerOpaqueTransaction = maxBytesPerOpaqueTransaction;
    this.maxApplicationTransactions = maxApplicationTransactions;
  }

  public static SpecConfigMerge required(final SpecConfig specConfig) {
    return specConfig
        .toVersionMerge()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected merge spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  public static <T> T required(
      final SpecConfig specConfig, final Function<SpecConfigMerge, T> ctr) {
    return ctr.apply(
        specConfig
            .toVersionMerge()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected merge spec config but got: "
                            + specConfig.getClass().getSimpleName())));
  }

  public Bytes4 getMergeForkVersion() {
    return mergeForkVersion;
  }

  public UInt64 getMergeForkSlot() {
    return mergeForkSlot;
  }

  public long getTransitionTotalDifficulty() {
    return transitionTotalDifficulty;
  }

  public int getMaxBytesPerOpaqueTransaction() {
    return maxBytesPerOpaqueTransaction;
  }

  public int getMaxApplicationTransactions() {
    return maxApplicationTransactions;
  }

  @Override
  public Optional<SpecConfigMerge> toVersionMerge() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpecConfigMerge that = (SpecConfigMerge) o;
    return maxBytesPerOpaqueTransaction == that.maxBytesPerOpaqueTransaction
        && maxApplicationTransactions == that.maxApplicationTransactions
        && Objects.equals(mergeForkVersion, that.mergeForkVersion)
        && Objects.equals(mergeForkSlot, that.mergeForkSlot)
        && transitionTotalDifficulty == that.transitionTotalDifficulty;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mergeForkVersion,
        mergeForkSlot,
        transitionTotalDifficulty,
        maxBytesPerOpaqueTransaction,
        maxApplicationTransactions);
  }
}
