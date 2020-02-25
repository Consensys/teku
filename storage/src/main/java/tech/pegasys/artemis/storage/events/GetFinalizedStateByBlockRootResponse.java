/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage.events;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.BeaconState;

public class GetFinalizedStateByBlockRootResponse {
  private final Bytes32 blockRoot;
  private final Optional<BeaconState> state;

  public GetFinalizedStateByBlockRootResponse(
      final Bytes32 blockRoot, final Optional<BeaconState> state) {
    this.blockRoot = blockRoot;
    this.state = state;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public Optional<BeaconState> getState() {
    return state;
  }
}
