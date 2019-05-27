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
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.Level;
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
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class AttestationUtil {

  private static final ALogger LOG = new ALogger(AttestationUtil.class.getName());
  /**
   * Returns true if the attestation is verified
   *
   * @param state
   * @param attestation
   * @return boolean
   */
  public static boolean verifyAttestation(BeaconState state, Attestation attestation) {
    // TODO
    return true;
  }

  /**
   * Creates attestations for all the Validators in our validator set, given that they are in
   * CrosslinkCommittees that are appointed to attest
   *
   * @param headState
   * @param headBlock
   * @param validatorSet
   * @return attestations
   */
  public static List<Attestation> createAttestations(
      BeaconState headState,
      BeaconBlock headBlock,
      HashMap<BLSPublicKey, BLSKeyPair> validatorSet) {

    // Get variables necessary that can be shared among Attestations of all validators
    long slot = headState.getSlot();
    ArrayList<CrosslinkCommittee> crosslinkCommittees =
        BeaconStateUtil.get_crosslink_committees_at_slot(headState, slot);
    Bytes32 headBlockRoot = headBlock.signed_root("signature");
    Bytes32 crosslinkDataRoot = Bytes32.ZERO;
    UnsignedLong epochStartSlot =
        BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.get_current_epoch(headState));
    Bytes32 epochBoundaryRoot;
    if (epochStartSlot.compareTo(slot) == 0) {
      epochBoundaryRoot = headBlock.signed_root("signature");
    } else {
      epochBoundaryRoot = BeaconStateUtil.get_block_root(headState, epochStartSlot);
    }
    UnsignedLong sourceEpoch = headState.getCurrent_justified_epoch();
    Bytes32 sourceRoot = headState.getCurrent_justified_root();

    // Create attestations specific to each Validator
    List<Attestation> attestations = new ArrayList<>();
    for (CrosslinkCommittee crosslinkCommittee : crosslinkCommittees) {
      for (Integer validatorIndex : crosslinkCommittee.getCommittee()) {

        // Skip if attester is in not in our validatorSet
        BLSPublicKey attesterPubkey =
            headState.getValidator_registry().get(validatorIndex).getPubkey();
        if (!validatorSet.containsKey(attesterPubkey)) {
          continue;
        }

        // Get variables specific to each Attestation
        UnsignedLong shard = crosslinkCommittee.getShard();
        Crosslink previousCrosslink =
            headState.getLatest_crosslinks().get(shard.intValue() % Constants.SHARD_COUNT);

        // Set attestation data
        AttestationData attestationData =
            new AttestationData(
                slot,
                headBlockRoot,
                sourceEpoch,
                sourceRoot,
                epochBoundaryRoot,
                shard,
                previousCrosslink,
                crosslinkDataRoot);

        // Create aggregation bitfield
        int indexIntoCommittee = crosslinkCommittee.getCommittee().indexOf(validatorIndex);
        int array_length = Math.toIntExact((crosslinkCommittee.getCommittee().size() + 7) / 8);
        byte[] aggregation_bitfield = new byte[array_length];
        aggregation_bitfield[indexIntoCommittee / 8] |= (byte) (1 << (indexIntoCommittee % 8L));

        // Create custody_bitfield
        Bytes custody_bitfield = Bytes.wrap(new byte[array_length]);
        AttestationDataAndCustodyBit attestation_data_and_custody_bit =
            new AttestationDataAndCustodyBit(attestationData, false);

        // Sign attestation data
        Bytes32 attestation_message_to_sign = attestation_data_and_custody_bit.hash_tree_root();
        BLSSignature signed_attestation_data =
            BLSSignature.sign(
                validatorSet.get(attesterPubkey),
                attestation_message_to_sign,
                BeaconStateUtil.get_domain(
                    headState.getFork(),
                    BeaconStateUtil.slot_to_epoch(attestationData.getSlot()),
                    Constants.DOMAIN_ATTESTATION));

        // Form attestation
        Attestation attestation =
            new Attestation(
                Bytes.wrap(aggregation_bitfield),
                attestationData,
                custody_bitfield,
                signed_attestation_data);

        attestations.add(attestation);
      }
    }
    return attestations;
  }

  public static List<Attestation> getAttestationsUntilSlot(
      PriorityBlockingQueue<Attestation> attestationsQueue, long slot) {
    List<Attestation> attestations = new ArrayList<>();
    if (Objects.nonNull(attestationsQueue) && attestationsQueue.size() > 0) {
      while (Objects.nonNull(attestationsQueue.peek())
          && attestationsQueue.peek().getSlot() <= slot) {
        attestations.add(attestationsQueue.remove());
      }
    }
    return attestations;
  }
}
