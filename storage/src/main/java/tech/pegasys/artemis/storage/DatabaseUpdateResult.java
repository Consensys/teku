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

package tech.pegasys.artemis.storage;

import java.util.Collections;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public interface DatabaseUpdateResult {

  static DatabaseUpdateResult failed(final RuntimeException error) {
    return new FailedDatabaseUpdateResult(error);
  }

  static DatabaseUpdateResult successful(
      final Set<Bytes32> prunedBlockRoots, final Set<Checkpoint> prunedCheckpoints) {
    return new SuccessfulDatabaseUpdateResult(prunedBlockRoots, prunedCheckpoints);
  }

  static DatabaseUpdateResult successfulWithNothingPruned() {
    return new SuccessfulDatabaseUpdateResult(Collections.emptySet(), Collections.emptySet());
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

  class SuccessfulDatabaseUpdateResult implements DatabaseUpdateResult {
    private final Set<Bytes32> prunedBlockRoots;
    private final Set<Checkpoint> prunedCheckpoints;

    SuccessfulDatabaseUpdateResult(
        final Set<Bytes32> prunedBlockRoots, final Set<Checkpoint> prunedCheckpoints) {
      this.prunedBlockRoots = prunedBlockRoots;
      this.prunedCheckpoints = prunedCheckpoints;
    }

    @Override
    public boolean isSuccessful() {
      return true;
    }

    @Override
    public RuntimeException getError() {
      return null;
    }

    @Override
    public Set<Checkpoint> getPrunedCheckpoints() {
      return prunedCheckpoints;
    }

    @Override
    public Set<Bytes32> getPrunedBlockRoots() {
      return prunedBlockRoots;
    }
  }

  class FailedDatabaseUpdateResult implements DatabaseUpdateResult {
    private final RuntimeException error;

    FailedDatabaseUpdateResult(final RuntimeException error) {
      this.error = error;
    }

    @Override
    public boolean isSuccessful() {
      return false;
    }

    @Override
    public RuntimeException getError() {
      return error;
    }

    @Override
    public Set<Bytes32> getPrunedBlockRoots() {
      return Collections.emptySet();
    }

    @Override
    public Set<Checkpoint> getPrunedCheckpoints() {
      return Collections.emptySet();
    }
  }
}
