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

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;

public class AttestationUtil {

  public static Bitlist getAggregationBits(int committeeSize, int indexIntoCommittee) {
    // Create aggregation bitfield
    Bitlist aggregationBits = new Bitlist(committeeSize, MAX_VALIDATORS_PER_COMMITTEE);
    aggregationBits.setBit(indexIntoCommittee);
    return aggregationBits;
  }

  public static Bytes32 getAttestationMessageToSign(AttestationData attestationData) {
    return attestationData.hash_tree_root();
  }

  /**
   * Check if ``data_1`` and ``data_2`` are slashable according to Casper FFG rules.
   *
   * @param data_1
   * @param data_2
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_slashable_attestation_data</a>
   */
  public static boolean is_slashable_attestation_data(
      AttestationData data_1, AttestationData data_2) {
    return (
    // case 1: double vote || case 2: surround vote
    (!data_1.equals(data_2) && data_1.getTarget().getEpoch().equals(data_2.getTarget().getEpoch()))
        || (data_1.getSource().getEpoch().compareTo(data_2.getSource().getEpoch()) < 0
            && data_2.getTarget().getEpoch().compareTo(data_1.getTarget().getEpoch()) < 0));
  }

  /**
   * Return the indexed attestation corresponding to ``attestation``.
   *
   * @param state
   * @param attestation
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_indexed_attestation</a>
   */
  public static IndexedAttestation get_indexed_attestation(
      BeaconState state, Attestation attestation) {
    List<Integer> attesting_indices =
        get_attesting_indices(state, attestation.getData(), attestation.getAggregation_bits());

    return new IndexedAttestation(
        new SSZList<>(
            attesting_indices.stream()
                .sorted()
                .map(UnsignedLong::valueOf)
                .collect(Collectors.toList()),
            MAX_VALIDATORS_PER_COMMITTEE,
            UnsignedLong.class),
        attestation.getData(),
        attestation.getAggregate_signature());
  }

  /**
   * Return the sorted attesting indices corresponding to ``data`` and ``bits``.
   *
   * @param state
   * @param data
   * @param bits
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_attesting_indices</a>
   */
  public static List<Integer> get_attesting_indices(
      BeaconState state, AttestationData data, Bitlist bits) {
    List<Integer> committee = get_beacon_committee(state, data.getSlot(), data.getIndex());

    Set<Integer> attesting_indices = new HashSet<>();
    for (int i = 0; i < committee.size(); i++) {
      int index = committee.get(i);
      int bitfieldBit = bits.getBit(i);
      if (bitfieldBit == 1) attesting_indices.add(index);
    }
    return new ArrayList<>(attesting_indices);
  }

  /**
   * Verify validity of ``indexed_attestation``.
   *
   * @param state
   * @param indexed_attestation
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_indexed_attestation</a>
   */
  public static Boolean is_valid_indexed_attestation(
      BeaconState state, IndexedAttestation indexed_attestation) {
    List<UnsignedLong> attesting_indices = indexed_attestation.getAttesting_indices();

    if (!(attesting_indices.size() <= MAX_VALIDATORS_PER_COMMITTEE)) {
      STDOUT.log(
          Level.WARN, "AttestationUtil.is_valid_indexed_attestation: Verify max number of indices");
      return false;
    }

    List<UnsignedLong> bit_0_indices_sorted = new ArrayList<>(attesting_indices);
    Collections.sort(bit_0_indices_sorted);
    if (!attesting_indices.equals(bit_0_indices_sorted)) {
      STDOUT.log(
          Level.WARN, "AttestationUtil.is_valid_indexed_attestation: Verify indices are sorted");
      return false;
    }

    List<Validator> validators = state.getValidators();
    BLSPublicKey pubkey =
        bls_aggregate_pubkeys(
            attesting_indices.stream()
                .map(i -> toIntExact(i.longValue()))
                .map(i -> validators.get(i).getPubkey())
                .collect(Collectors.toList()));

    Bytes32 message_hash = indexed_attestation.getData().hash_tree_root();

    BLSSignature signature = indexed_attestation.getSignature();
    Bytes domain =
        get_domain(
            state, DOMAIN_BEACON_ATTESTER, indexed_attestation.getData().getTarget().getEpoch());
    if (!BLSVerify.bls_verify(pubkey, message_hash, signature, domain)) {
      STDOUT.log(
          Level.WARN, "AttestationUtil.is_valid_indexed_attestation: Verify aggregate signature");
      return false;
    }
    return true;
  }

  // Set bits of the newAttestation on the oldBitlist
  // return true if any new bit was set
  public static boolean setBitsForNewAttestation(Bitlist oldBitlist, Attestation newAttesation) {
    Bitlist newBitlist = newAttesation.getAggregation_bits();
    if (oldBitlist.getCurrentSize() != newBitlist.getCurrentSize())
      throw new UnsupportedOperationException("Attestation bitlist size's don't match");
    boolean representsNewAttester = false;
    for (int i = 0; i < oldBitlist.getCurrentSize(); i++) {
      if (newBitlist.getBit(i) == 1 && oldBitlist.getBit(i) == 0) {
        oldBitlist.setBit(i);
        representsNewAttester = true;
      }
    }
    return representsNewAttester;
  }

  public static boolean representsNewAttester(
      Attestation oldAttestation, Attestation newAttestation) {
    int newAttesterIndex = getAttesterIndexIntoCommittee(newAttestation);
    return oldAttestation.getAggregation_bits().getBit(newAttesterIndex) == 0;
  }

  // Returns the index of the first attester in the Attestation
  public static int getAttesterIndexIntoCommittee(Attestation attestation) {
    Bitlist aggregationBits = attestation.getAggregation_bits();
    for (int i = 0; i < aggregationBits.getCurrentSize(); i++) {
      int bitfieldBit = aggregationBits.getBit(i);
      if (bitfieldBit == 1) {
        return i;
      }
    }
    throw new UnsupportedOperationException("Attestation doesn't have any aggregation bit set");
  }

  // Returns the indices of the attesters in the Attestation
  public static List<Integer> getAttesterIndicesIntoCommittee(Bitlist aggregationBits) {
    List<Integer> attesterIndices = new ArrayList<>();
    for (int i = 0; i < aggregationBits.getCurrentSize(); i++) {
      int bitfieldBit = aggregationBits.getBit(i);
      if (bitfieldBit == 1) {
        attesterIndices.add(i);
      }
    }
    return attesterIndices;
  }

  public static boolean isSingleAttester(Attestation attestation) {
    Bitlist aggregationBitfield = attestation.getAggregation_bits();
    int count = 0;
    for (int i = 0; i < aggregationBitfield.getCurrentSize(); i++) {
      int bitfieldBit = aggregationBitfield.getBit(i);
      if (bitfieldBit == 1) count++;
    }

    if (count == 1) {
      return true;
    } else {
      return false;
    }
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public static AttestationData getGenericAttestationData(BeaconState state, BeaconBlock block) {
    UnsignedLong slot = state.getSlot();
    // Get variables necessary that can be shared among Attestations of all validators
    Bytes32 beacon_block_root = block.signing_root("signature");
    UnsignedLong start_slot = compute_start_slot_at_epoch(get_current_epoch(state));
    Bytes32 epoch_boundary_block_root =
        start_slot.compareTo(slot) == 0
            ? block.signing_root("signature")
            : get_block_root_at_slot(state, start_slot);
    Checkpoint source = state.getCurrent_justified_checkpoint();
    Checkpoint target = new Checkpoint(get_current_epoch(state), epoch_boundary_block_root);

    // Set attestation data
    return new AttestationData(slot, UnsignedLong.ZERO, beacon_block_root, source, target);
  }
}
