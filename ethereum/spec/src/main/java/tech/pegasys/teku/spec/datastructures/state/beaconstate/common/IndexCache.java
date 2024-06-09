/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface IndexCache {
  boolean invalidateWithNewValue(
      final BLSPublicKey pubKey, final int updatedIndex, final boolean isFinalizedState);

  Optional<Integer> find(final int stateValidatorCount, final BLSPublicKey publicKey);

  void updateLatestFinalizedIndex(final BeaconState finalizedState);

  int getSize();

  // these interfaces are only relevant on final cache, hot cache calls through to final cache.
  int getLastCachedIndex();

  UInt64 getFinalizedSlot();

  int getLatestFinalizedIndex();
}
