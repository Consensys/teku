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

import java.util.Optional;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class WeakSubjectivityUpdate {
  private final Optional<Checkpoint> weakSubjectivityCheckpoint;

  private WeakSubjectivityUpdate(Optional<Checkpoint> weakSubjectivityCheckpoint) {
    this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
  }

  public static WeakSubjectivityUpdate clearWeakSubjectivityCheckpoint() {
    return new WeakSubjectivityUpdate(Optional.empty());
  }

  public static WeakSubjectivityUpdate setWeakSubjectivityCheckpoint(
      final Checkpoint newCheckpoint) {
    return new WeakSubjectivityUpdate(Optional.of(newCheckpoint));
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return weakSubjectivityCheckpoint;
  }
}
