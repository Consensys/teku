/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecConfigRayonism extends DelegatingSpecConfig {

  // Fork
  private final Bytes4 mergeForkVersion;
  private final UInt64 mergeForkSlot;

  // Transition
  private final long transitionTotalDifficulty;

  public SpecConfigRayonism(
      SpecConfig specConfig,
      Bytes4 mergeForkVersion,
      UInt64 mergeForkSlot,
      long transitionTotalDifficulty) {
    super(specConfig);
    this.mergeForkVersion = mergeForkVersion;
    this.mergeForkSlot = mergeForkSlot;
    this.transitionTotalDifficulty = transitionTotalDifficulty;
  }

  public static SpecConfigRayonism required(final SpecConfig specConfig) {
    return specConfig
        .toVersionRayonism()
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
            .toVersionRayonism()
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

  @Override
  public Optional<SpecConfigRayonism> toVersionRayonism() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpecConfigRayonism that = (SpecConfigRayonism) o;
    return Objects.equals(mergeForkVersion, that.mergeForkVersion)
        && Objects.equals(mergeForkSlot, that.mergeForkSlot)
        && transitionTotalDifficulty == that.transitionTotalDifficulty;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mergeForkVersion, mergeForkSlot, transitionTotalDifficulty);
  }
}
