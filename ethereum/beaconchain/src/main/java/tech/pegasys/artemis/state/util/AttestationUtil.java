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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Iterator;

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainoperations.AttestationData;
import tech.pegasys.artemis.datastructures.beaconchainstate.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.state.BeaconState;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.uint.UInt256Bytes;

import static java.lang.Math.toIntExact;

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
            && record.getData().getJustified_slot().longValue() == state.getJustified_slot())
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
          .get(UnsignedLong.valueOf(slot % state.getLatest_block_roots().size()));
    throw new BlockValidationException("Desired block root not within the provided bounds");
  }

  public static ArrayList<Integer> get_attestation_participants(BeaconState state, AttestationData attestation_data , Bytes32 participation_bitfield){
    ArrayList<ShardCommittee> shard_committees = state.get_shard_committees_at_slot(state, toIntExact(attestation_data.getSlot()));

    //Find the relevant committee
    Iterator<ShardCommittee> itr = shard_committees.iterator();
    while(itr.hasNext()){
      ShardCommittee shard_committee = itr.next();
//      if(participation_bitfield.compareTo((Bytes32) UInt256Bytes.of((long)ceil_div8(shard_committee.getCommittee().size())))){
//
//      }
    }
    return null;
  }

  public static int ceil_div8(int input){
    return (int) Math.ceil(((double)input)/8.0d);
  }
}
