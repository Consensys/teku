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

package tech.pegasys.teku.storage.events;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class WeakSubjectivityState {
  private final Optional<Checkpoint> checkpoint;

  private WeakSubjectivityState(Optional<Checkpoint> checkpoint) {
    this.checkpoint = checkpoint;
  }

  public static WeakSubjectivityState create(Optional<Checkpoint> checkpoint) {
    return new WeakSubjectivityState(checkpoint);
  }

  public static WeakSubjectivityState empty() {
    return new WeakSubjectivityState(Optional.empty());
  }

  public Optional<Checkpoint> getCheckpoint() {
    return checkpoint;
  }

  public boolean isEmpty() {
    return checkpoint.isEmpty();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WeakSubjectivityState that = (WeakSubjectivityState) o;
    return Objects.equals(checkpoint, that.checkpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkpoint);
  }

  @Override
  public String toString() {
    return "WeakSubjectivityState{" + "checkpoint=" + checkpoint + '}';
  }
}
