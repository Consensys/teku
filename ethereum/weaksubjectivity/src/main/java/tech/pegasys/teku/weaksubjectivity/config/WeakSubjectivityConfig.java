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

public class WeakSubjectivityConfig {

  private final Optional<Checkpoint> weakSubjectivityCheckpoint;

  private WeakSubjectivityConfig(Optional<Checkpoint> weakSubjectivityCheckpoint) {
    checkNotNull(weakSubjectivityCheckpoint);

    this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return weakSubjectivityCheckpoint;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private WeakSubjectivityParameterParser parser = new WeakSubjectivityParameterParser();

    private Optional<Checkpoint> weakSubjectivityCheckpoint = Optional.empty();

    private Builder() {}

    public WeakSubjectivityConfig build() {
      return new WeakSubjectivityConfig(weakSubjectivityCheckpoint);
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
  }
}
