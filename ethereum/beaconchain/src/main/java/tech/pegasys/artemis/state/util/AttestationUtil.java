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

package tech.pegasys.artemis.state.util;

import java.util.ArrayList;
import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainstate.PendingAttestationRecord;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.state.BeaconState;
import tech.pegasys.artemis.util.uint.UInt64;

public class AttestationUtil {

  public static ArrayList<PendingAttestationRecord> get_current_epoch_attestations(
      BeaconState state, ArrayList<PendingAttestationRecord> latest_attestations) {
    ArrayList<PendingAttestationRecord> current_epoch_attestations = new ArrayList<>();
    if (latest_attestations != null) {
      for (PendingAttestationRecord record : latest_attestations) {
        if (isAttestationCurrentEpoch(state, record)) current_epoch_attestations.add(record);
      }
    }
    return current_epoch_attestations;
  }

  private static boolean isAttestationCurrentEpoch(
      BeaconState state, PendingAttestationRecord record) {
    long epoch_lower_boundary = state.getSlot() - Constants.EPOCH_LENGTH;
    long epoch_upper_boundary = state.getSlot();
    return (record.getData().getSlot() <= epoch_lower_boundary
        && record.getData().getSlot() > epoch_upper_boundary);
  }

  public static ArrayList<PendingAttestationRecord> get_current_epoch_boundary_attestations(
      BeaconState state, ArrayList<PendingAttestationRecord> current_epoch_attestations)
      throws Exception {
    ArrayList<PendingAttestationRecord> current_epoch_boundary_attestations = new ArrayList<>();
    if (current_epoch_attestations != null) {
      for (PendingAttestationRecord record : current_epoch_boundary_attestations) {
        if (record
                .getData()
                .getEpoch_boundary_hash()
                .equals(get_block_root(state, record.getData().getSlot() - Constants.EPOCH_LENGTH))
            && record.getData().getJustified_slot().getValue() == state.getJustified_slot())
          current_epoch_attestations.add(record);
      }
    }
    return current_epoch_boundary_attestations;
  }

  // https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#get_block_root
  public static Hash get_block_root(BeaconState state, long slot) throws Exception {
    long slot_upper_bound = slot + state.getLatest_block_roots().size();
    if ((state.getSlot() <= slot_upper_bound) || slot < state.getSlot())
      return state
          .getLatest_block_roots()
          .get(UInt64.valueOf(slot % state.getLatest_block_roots().size()));
    throw new BlockValidationException("Desired block root not within the provided bounds");
  }
}
