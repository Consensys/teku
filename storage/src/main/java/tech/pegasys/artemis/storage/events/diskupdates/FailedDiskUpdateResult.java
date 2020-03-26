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

package tech.pegasys.artemis.storage.events.diskupdates;

import java.util.Collections;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class FailedDiskUpdateResult implements DiskUpdateResult {
  private final RuntimeException error;

  FailedDiskUpdateResult(final RuntimeException error) {
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
