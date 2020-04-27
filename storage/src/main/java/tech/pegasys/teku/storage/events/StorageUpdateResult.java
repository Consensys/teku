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

import java.util.Collections;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public interface StorageUpdateResult {

  static StorageUpdateResult failed(final RuntimeException error) {
    return new FailedStorageUpdateResult(error);
  }

  static StorageUpdateResult successful(
      final Set<Bytes32> prunedBlockRoots, final Set<Checkpoint> prunedCheckpoints) {
    return new SuccessfulStorageUpdateResult(prunedBlockRoots, prunedCheckpoints);
  }

  static StorageUpdateResult successfulWithNothingPruned() {
    return new SuccessfulStorageUpdateResult(Collections.emptySet(), Collections.emptySet());
  }

  /** @return {@code true} if the update was successfully processed */
  boolean isSuccessful();

  /** @return If the result is unsuccessful, returns the error, otherwise null. */
  RuntimeException getError();

  /**
   * @return If the update was successful returns the set of block roots that were pruned from
   *     storage. Otherwise, returns an empty collection.
   */
  Set<Bytes32> getPrunedBlockRoots();
  /**
   * @return If the update was successful returns the set of checkpoints that were pruned from
   *     storage. Otherwise, returns an empty collection.
   */
  Set<Checkpoint> getPrunedCheckpoints();
}
