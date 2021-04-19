package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecConfigRayonism extends DelegatingSpecConfig {

  // Fork
  private final Bytes4 rayonismForkVersion;
  private final UInt64 rayonismForkSlot;

  // Transition
  private final long transitionTotalDifficulty;

  public SpecConfigRayonism(
      SpecConfig specConfig,
      Bytes4 rayonismForkVersion,
      UInt64 rayonismForkSlot,
      long transitionTotalDifficulty) {
    super(specConfig);
    this.rayonismForkVersion = rayonismForkVersion;
    this.rayonismForkSlot = rayonismForkSlot;
    this.transitionTotalDifficulty = transitionTotalDifficulty;
  }

  public static SpecConfigRayonism required(final SpecConfig specConfig) {
    return specConfig
        .toVersionMerge()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected merge spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  public static <T> T required(
      final SpecConfig specConfig, final Function<SpecConfigRayonism, T> ctr) {
    return ctr.apply(
        specConfig
            .toVersionMerge()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected merge spec config but got: "
                            + specConfig.getClass().getSimpleName())));
  }

  public Bytes4 getRayonismForkVersion() {
    return rayonismForkVersion;
  }

  public UInt64 getRayonismForkSlot() {
    return rayonismForkSlot;
  }

  public long getTransitionTotalDifficulty() {
    return transitionTotalDifficulty;
  }

  @Override
  public Optional<SpecConfigRayonism> toVersionMerge() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpecConfigRayonism that = (SpecConfigRayonism) o;
    return Objects.equals(rayonismForkVersion, that.rayonismForkVersion)
        && Objects.equals(rayonismForkSlot, that.rayonismForkSlot)
        && transitionTotalDifficulty == that.transitionTotalDifficulty;
  }

  @Override
  public int hashCode() {
    return Objects.hash(rayonismForkVersion, rayonismForkSlot, transitionTotalDifficulty);
  }
}
