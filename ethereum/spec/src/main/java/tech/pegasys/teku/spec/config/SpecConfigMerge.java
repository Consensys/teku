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
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecConfigMerge extends DelegatingSpecConfig {

  // Fork
  private final Bytes4 mergeForkVersion;
  private final UInt64 mergeForkEpoch;

  // Transition
  private final UInt64 targetSecondsToMerge;
  private final UInt256 minAnchorPowBlockDifficulty;

  public SpecConfigMerge(
      SpecConfig specConfig,
      Bytes4 mergeForkVersion,
      UInt64 mergeForkEpoch,
      UInt64 targetSecondsToMerge,
      UInt256 minAnchorPowBlockDifficulty) {
    super(specConfig);
    this.mergeForkVersion = mergeForkVersion;
    this.mergeForkEpoch = mergeForkEpoch;
    this.targetSecondsToMerge = targetSecondsToMerge;
    this.minAnchorPowBlockDifficulty = minAnchorPowBlockDifficulty;
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

  public UInt64 getMergeForkEpoch() {
    return mergeForkEpoch;
  }

  public UInt64 getTargetSecondsToMerge() {
    return targetSecondsToMerge;
  }

  public UInt256 getMinAnchorPowBlockDifficulty() {
    return minAnchorPowBlockDifficulty;
  }

  @Override
  public Optional<SpecConfigMerge> toVersionMerge() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SpecConfigMerge that = (SpecConfigMerge) o;
    return Objects.equals(mergeForkVersion, that.mergeForkVersion)
        && Objects.equals(mergeForkEpoch, that.mergeForkEpoch)
        && Objects.equals(targetSecondsToMerge, that.targetSecondsToMerge)
        && Objects.equals(minAnchorPowBlockDifficulty, that.minAnchorPowBlockDifficulty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mergeForkVersion, mergeForkEpoch, targetSecondsToMerge, minAnchorPowBlockDifficulty);
  }
}
