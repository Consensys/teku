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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;

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

  public static WeakSubjectivityConfig from(final WeakSubjectivityState state) {
    return builder().weakSubjectivityCheckpoint(state.getCheckpoint()).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public WeakSubjectivityConfig updated(final Consumer<Builder> updater) {
    Builder builder = copy();
    updater.accept(builder);
    return builder.build();
  }

  private Builder copy() {
    Builder builder = WeakSubjectivityConfig.builder().safetyDecay(safetyDecay);
    weakSubjectivityCheckpoint.ifPresent(builder::weakSubjectivityCheckpoint);
    return builder;
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return weakSubjectivityCheckpoint;
  }

  public UInt64 getSafetyDecay() {
    return safetyDecay;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WeakSubjectivityConfig that = (WeakSubjectivityConfig) o;
    return Objects.equals(safetyDecay, that.safetyDecay)
        && Objects.equals(weakSubjectivityCheckpoint, that.weakSubjectivityCheckpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(safetyDecay, weakSubjectivityCheckpoint);
  }

  @Override
  public String toString() {
    return "WeakSubjectivityConfig{"
        + "safetyDecay="
        + safetyDecay
        + ", weakSubjectivityCheckpoint="
        + weakSubjectivityCheckpoint
        + '}';
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
      return weakSubjectivityCheckpoint(Optional.of(weakSubjectivityCheckpoint));
    }

    public Builder weakSubjectivityCheckpoint(String weakSubjectivityCheckpoint) {
      checkNotNull(weakSubjectivityCheckpoint);
      final Checkpoint checkpoint = parser.parseCheckpoint(weakSubjectivityCheckpoint);
      return weakSubjectivityCheckpoint(checkpoint);
    }

    public Builder weakSubjectivityCheckpoint(Optional<Checkpoint> weakSubjectivityCheckpoint) {
      checkNotNull(weakSubjectivityCheckpoint);
      this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
      return this;
    }

    public Builder safetyDecay(UInt64 safetyDecay) {
      this.safetyDecay = safetyDecay;
      return this;
    }
  }
}
