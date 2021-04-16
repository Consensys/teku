/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.synccommittee;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeStateUtils {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final RecentChainData recentChainData;

  public SyncCommitteeStateUtils(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<Optional<BeaconStateAltair>> getStateForSyncCommittee(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return recentChainData
        .retrieveBlockState(beaconBlockRoot)
        // If the block is from an earlier epoch we need to process slots to the current epoch
        .<Optional<BeaconState>>thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return Optional.empty();
              }
              final SyncCommitteeUtil syncCommitteeUtil =
                  spec.getSyncCommitteeUtil(slot).orElseThrow();
              final UInt64 contributionEpoch = spec.computeEpochAtSlot(slot);
              final UInt64 minEpoch =
                  syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(contributionEpoch);
              final UInt64 stateEpoch = spec.getCurrentEpoch(maybeState.get());
              if (stateEpoch.isLessThan(minEpoch)) {
                LOG.warn(
                    "Ignoring sync committee gossip because it refers to a block that is too old. Block root {} from epoch {} is more than two sync committee periods before current epoch {}",
                    beaconBlockRoot,
                    stateEpoch,
                    contributionEpoch);
                return Optional.empty();
              } else {
                return maybeState;
              }
            })
        .thenApply(maybeState -> maybeState.flatMap(BeaconState::toVersionAltair));
  }
}
