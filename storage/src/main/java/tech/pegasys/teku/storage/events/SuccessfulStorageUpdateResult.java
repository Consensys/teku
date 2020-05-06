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

import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class SuccessfulStorageUpdateResult implements StorageUpdateResult {
  private final Set<Bytes32> prunedBlockRoots;
  private final Set<Checkpoint> prunedCheckpoints;

  public SuccessfulStorageUpdateResult(
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
