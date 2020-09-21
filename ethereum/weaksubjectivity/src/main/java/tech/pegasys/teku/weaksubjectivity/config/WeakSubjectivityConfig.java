/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.weaksubjectivity.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WeakSubjectivityConfig {
  public static UInt64 DEFAULT_SAFETY_DECAY = UInt64.valueOf(10);

  private final UInt64 safetyDecay;
  private final Optional<Checkpoint> weakSubjectivityCheckpoint;

  private WeakSubjectivityConfig(
      UInt64 safetyDecay, Optional<Checkpoint> weakSubjectivityCheckpoint) {
    this.safetyDecay = safetyDecay;
    checkNotNull(weakSubjectivityCheckpoint);

    this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
  }

  public static WeakSubjectivityConfig defaultConfig() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return weakSubjectivityCheckpoint;
  }

  public UInt64 getSafetyDecay() {
    return safetyDecay;
  }

  public static class Builder {
    private WeakSubjectivityParameterParser parser = new WeakSubjectivityParameterParser();

    private UInt64 safetyDecay = DEFAULT_SAFETY_DECAY;
    private Optional<Checkpoint> weakSubjectivityCheckpoint = Optional.empty();

    private Builder() {}

    public WeakSubjectivityConfig build() {
      return new WeakSubjectivityConfig(safetyDecay, weakSubjectivityCheckpoint);
    }

    public Builder weakSubjectivityCheckpoint(Checkpoint weakSubjectivityCheckpoint) {
      checkNotNull(weakSubjectivityCheckpoint);
      this.weakSubjectivityCheckpoint = Optional.of(weakSubjectivityCheckpoint);
      return this;
    }

    public Builder weakSubjectivityCheckpoint(String weakSubjectivityCheckpoint) {
      checkNotNull(weakSubjectivityCheckpoint);
      final Checkpoint checkpoint = parser.parseCheckpoint(weakSubjectivityCheckpoint);
      return weakSubjectivityCheckpoint(checkpoint);
    }

    public Builder safetyDecay(UInt64 safetyDecay) {
      this.safetyDecay = safetyDecay;
      return this;
    }
  }
}
