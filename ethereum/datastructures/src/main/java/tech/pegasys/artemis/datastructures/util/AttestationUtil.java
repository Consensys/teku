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

package tech.pegasys.artemis.datastructures.util;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class AttestationUtil {

  /**
   * Returns true if the attestation is verified
   *
   * @param state
   * @param attestation
   * @return boolean
   */
  public static boolean verifyAttestation(BeaconState state, Attestation attestation) {
    return true;
  }

  public static List<Triple<BLSPublicKey, Integer, CrosslinkCommittee>> getAttesterInformation(
      BeaconState headState, HashMap<BLSPublicKey, BLSKeyPair> validatorSet) {

    UnsignedLong slot = headState.getSlot();
    List<CrosslinkCommittee> crosslinkCommittees =
        BeaconStateUtil.get_crosslink_committees_at_slot(headState, slot);

    List<Triple<BLSPublicKey, Integer, CrosslinkCommittee>> attesters = new ArrayList<>();

    for (CrosslinkCommittee crosslinkCommittee : crosslinkCommittees) {
      int indexIntoCommittee = 0;
      for (Integer validatorIndex : crosslinkCommittee.getCommittee()) {

        BLSPublicKey attesterPubkey =
            headState.getValidator_registry().get(validatorIndex).getPubkey();
        if (validatorSet.containsKey(attesterPubkey)) {
          attesters.add(
              new ImmutableTriple<>(attesterPubkey, indexIntoCommittee, crosslinkCommittee));
        }
        indexIntoCommittee++;
      }
    }
    return attesters;
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public static AttestationData getGenericAttestationData(BeaconState state, BeaconBlock block) {
    // Get variables necessary that can be shared among Attestations of all validators
    UnsignedLong slot = state.getSlot();
    List<CrosslinkCommittee> crosslinkCommittees =
        BeaconStateUtil.get_crosslink_committees_at_slot(state, slot);
    Bytes32 headBlockRoot = block.signed_root("signature");
    Bytes32 crosslinkDataRoot = Bytes32.ZERO;
    UnsignedLong epochStartSlot =
        BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.get_current_epoch(state));
    Bytes32 epochBoundaryRoot;
    if (epochStartSlot.compareTo(slot) == 0) {
      epochBoundaryRoot = block.signed_root("signature");
    } else {
      epochBoundaryRoot = BeaconStateUtil.get_block_root(state, epochStartSlot);
    }
    UnsignedLong sourceEpoch = state.getCurrent_justified_epoch();
    Bytes32 sourceRoot = state.getCurrent_justified_root();

    // Set attestation data
    // TODO: change the generic shard number and crosslink usage to something safer
    AttestationData attestationData =
        new AttestationData(
            slot,
            headBlockRoot,
            sourceEpoch,
            sourceRoot,
            epochBoundaryRoot,
            UnsignedLong.MAX_VALUE,
            new Crosslink(UnsignedLong.MAX_VALUE, Bytes32.ZERO),
            crosslinkDataRoot);

    return attestationData;
  }

  public static Bytes getAggregationBitfield(int indexIntoCommittee, int arrayLength) {
    // Create aggregation bitfield
    byte[] aggregationBitfield = new byte[arrayLength];
    aggregationBitfield[indexIntoCommittee / 8] =
        (byte)
            (aggregationBitfield[indexIntoCommittee / 8]
                | (byte) Math.pow(2, (indexIntoCommittee % 8)));
    return Bytes.wrap(aggregationBitfield);
  }

  public static Bytes getCustodyBitfield(int arrayLength) {
    return Bytes.wrap(new byte[arrayLength]);
  }

  public static AttestationData completeAttestationData(
      BeaconState state, AttestationData attestationData, CrosslinkCommittee committee) {
    UnsignedLong shard = committee.getShard();
    attestationData.setShard(shard);
    Crosslink previousCrosslink =
        state.getLatest_crosslinks().get(shard.intValue() % Constants.SHARD_COUNT);
    attestationData.setPrevious_crosslink(previousCrosslink);
    return attestationData;
  }

  public static Bytes32 getAttestationMessageToSign(AttestationData attestationData) {
    AttestationDataAndCustodyBit attestation_data_and_custody_bit =
        new AttestationDataAndCustodyBit(attestationData, false);
    return attestation_data_and_custody_bit.hash_tree_root();
  }

  public static int getDomain(BeaconState state, AttestationData attestationData) {
    return BeaconStateUtil.get_domain(
            state.getFork(),
            BeaconStateUtil.slot_to_epoch(attestationData.getSlot()),
            Constants.DOMAIN_ATTESTATION)
        .intValue();
  }
}
