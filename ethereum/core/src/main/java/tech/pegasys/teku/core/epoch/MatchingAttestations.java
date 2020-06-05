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

package tech.pegasys.teku.core.epoch;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class MatchingAttestations {

  private final BeaconState state;
  private final Map<UnsignedLong, SSZList<PendingAttestation>> matchingSourceAttestationsByEpoch =
      new HashMap<>();
  private final Map<UnsignedLong, SSZList<PendingAttestation>> matchingTargetAttestationsByEpoch =
      new HashMap<>();

  public MatchingAttestations(final BeaconState state) {
    this.state = state;
  }

  public SSZList<PendingAttestation> getMatchingSourceAttestations(final UnsignedLong epoch) {
    return matchingSourceAttestationsByEpoch.computeIfAbsent(
        epoch, this::calculateMatchingSourceAttestations);
  }

  public SSZList<PendingAttestation> getMatchingTargetAttestations(final UnsignedLong epoch) {
    return matchingTargetAttestationsByEpoch.computeIfAbsent(
        epoch, this::calculateMatchingTargetAttestations);
  }

  public SSZList<PendingAttestation> getMatchingHeadAttestations(final UnsignedLong epoch) {
    // Only called once so no need to cache.
    return calculateMatchingHeadAttestations(epoch);
  }

  /**
   * Returns current or previous epoch attestations depending to the epoch passed in
   *
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private SSZList<PendingAttestation> calculateMatchingSourceAttestations(UnsignedLong epoch)
      throws IllegalArgumentException {
    checkArgument(
        get_current_epoch(state).equals(epoch) || get_previous_epoch(state).equals(epoch),
        "get_matching_source_attestations requested for invalid epoch");
    if (epoch.equals(get_current_epoch(state))) {
      return state.getCurrent_epoch_attestations();
    }
    return state.getPrevious_epoch_attestations();
  }

  /**
   * Returns source attestations that target the block root of the first block in the given epoch
   *
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private SSZList<PendingAttestation> calculateMatchingTargetAttestations(UnsignedLong epoch)
      throws IllegalArgumentException {
    return getMatchingSourceAttestations(epoch)
        .filter(a -> a.getData().getTarget().getRoot().equals(get_block_root(state, epoch)));
  }

  /**
   * Returns source attestations that have the same beacon head block as the one seen in state
   *
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private SSZList<PendingAttestation> calculateMatchingHeadAttestations(UnsignedLong epoch)
      throws IllegalArgumentException {
    return getMatchingTargetAttestations(epoch)
        .filter(
            a ->
                a.getData()
                    .getBeacon_block_root()
                    .equals(get_block_root_at_slot(state, a.getData().getSlot())));
  }
}
