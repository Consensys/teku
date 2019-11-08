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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_beacon_committee;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;

public class AttestationUtil {

  private static final ALogger STDOUT = new ALogger("stdout");

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

  public static List<Triple<BLSPublicKey, Integer, Committee>> getAttesterInformation(
      BeaconState headState,
      HashMap<UnsignedLong, List<Triple<List<Integer>, UnsignedLong, Integer>>>
          committeeAssignments) {
    UnsignedLong slot = headState.getSlot();
    return getAttesterInformation(headState, committeeAssignments, slot);
  }

  public static List<Triple<BLSPublicKey, Integer, Committee>> getAttesterInformation(
      BeaconState state,
      HashMap<UnsignedLong, List<Triple<List<Integer>, UnsignedLong, Integer>>>
          committeeAssignments,
      final UnsignedLong slot) {
    List<Triple<List<Integer>, UnsignedLong, Integer>> committeeAssignmentsForSlot =
        committeeAssignments.get(slot);
    List<Triple<BLSPublicKey, Integer, Committee>> attesters = new ArrayList<>();
    if (committeeAssignmentsForSlot != null) {
      for (int i = 0; i < committeeAssignmentsForSlot.size(); i++) {
        int validatorIndex = committeeAssignmentsForSlot.get(i).getRight();
        List<Integer> committee = committeeAssignmentsForSlot.get(i).getLeft();
        UnsignedLong index = committeeAssignmentsForSlot.get(i).getMiddle();
        int indexIntoCommittee = committee.indexOf(validatorIndex);

        Committee crosslinkCommittee = new Committee(index, committee);
        attesters.add(
            new MutableTriple<>(
                state.getValidators().get(validatorIndex).getPubkey(),
                indexIntoCommittee,
                crosslinkCommittee));
      }
    }
    return attesters;
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public static AttestationData getGenericAttestationData(BeaconState state, BeaconBlock block) {
    // Get variables necessary that can be shared among Attestations of all validators
    UnsignedLong slot = state.getSlot();
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

  public static Bitlist getAggregationBits(int committeeSize, int indexIntoCommittee) {
    // Create aggregation bitfield
    Bitlist aggregationBits = new Bitlist(committeeSize, MAX_VALIDATORS_PER_COMMITTEE);
    aggregationBits.setBit(indexIntoCommittee);
    return aggregationBits;
  }

  public static AttestationData completeAttestationCrosslinkData(
      BeaconState state, AttestationData attestation_data, Committee committee) {
    return attestation_data;
  }

  public static Bytes32 getAttestationMessageToSign(AttestationData attestationData) {
    AttestationDataAndCustodyBit attestation_data_and_custody_bit =
        new AttestationDataAndCustodyBit(attestationData, false);
    return attestation_data_and_custody_bit.hash_tree_root();
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
    List<Integer> custody_bit_1_indices =
        get_attesting_indices(state, attestation.getData(), attestation.getCustody_bitfield());

    checkArgument(
        attesting_indices.containsAll(custody_bit_1_indices),
        "get_indexed_attestation: custody_bit_1_indices is not a subset of attesting_indices");

    List<Integer> custody_bit_0_indices = new ArrayList<>();
    for (int i = 0; i < attesting_indices.size(); i++) {
      Integer attesting_index = attesting_indices.get(i);
      if (!custody_bit_1_indices.contains(attesting_index))
        custody_bit_0_indices.add(attesting_index);
    }
    return new IndexedAttestation(
        new SSZList<>(
            custody_bit_0_indices.stream()
                .sorted()
                .map(UnsignedLong::valueOf)
                .collect(Collectors.toList()),
            MAX_VALIDATORS_PER_COMMITTEE,
            UnsignedLong.class),
        new SSZList<>(
            custody_bit_1_indices.stream()
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
    List<UnsignedLong> bit_0_indices = indexed_attestation.getCustody_bit_0_indices();
    List<UnsignedLong> bit_1_indices = indexed_attestation.getCustody_bit_1_indices();

    if (!(bit_1_indices.size() == 0)) {
      STDOUT.log(
          Level.WARN,
          "AttestationUtil.is_valid_indexed_attestation: Verify no index has custody bit equal to 1 [to be removed in phase 1]");
      return false;
    }
    if (!((bit_0_indices.size() + bit_1_indices.size()) <= MAX_VALIDATORS_PER_COMMITTEE)) {
      STDOUT.log(
          Level.WARN, "AttestationUtil.is_valid_indexed_attestation: Verify max number of indices");
      return false;
    }
    if (!(intersection(bit_0_indices, bit_1_indices).size() == 0)) {
      STDOUT.log(
          Level.WARN,
          "AttestationUtil.is_valid_indexed_attestation: Verify index sets are disjoint");
      return false;
    }
    List<UnsignedLong> bit_0_indices_sorted = new ArrayList<>(bit_0_indices);
    Collections.sort(bit_0_indices_sorted);
    List<UnsignedLong> bit_1_indices_sorted = new ArrayList<>(bit_1_indices);
    Collections.sort(bit_1_indices_sorted);
    if (!(bit_0_indices.equals(bit_0_indices_sorted)
        && bit_1_indices.equals(bit_1_indices_sorted))) {
      STDOUT.log(
          Level.WARN, "AttestationUtil.is_valid_indexed_attestation: Verify indices are sorted");
      return false;
    }

    List<Validator> validators = state.getValidators();
    List<BLSPublicKey> pubkeys = new ArrayList<>();
    pubkeys.add(
        bls_aggregate_pubkeys(
            bit_0_indices.stream()
                .map(i -> toIntExact(i.longValue()))
                .map(i -> validators.get(i).getPubkey())
                .collect(Collectors.toList())));
    pubkeys.add(
        bls_aggregate_pubkeys(
            bit_1_indices.stream()
                .map(i -> toIntExact(i.longValue()))
                .map(i -> validators.get(i).getPubkey())
                .collect(Collectors.toList())));

    List<Bytes32> message_hashes = new ArrayList<>();
    message_hashes.add(
        new AttestationDataAndCustodyBit(indexed_attestation.getData(), false).hash_tree_root());
    message_hashes.add(
        new AttestationDataAndCustodyBit(indexed_attestation.getData(), true).hash_tree_root());

    BLSSignature signature = indexed_attestation.getSignature();
    Bytes domain =
        get_domain(
            state, DOMAIN_BEACON_ATTESTER, indexed_attestation.getData().getTarget().getEpoch());
    if (!BLSVerify.bls_verify_multiple(pubkeys, message_hashes, signature, domain)) {
      STDOUT.log(
          Level.WARN, "AttestationUtil.is_valid_indexed_attestation: Verify aggregate signature");
      return false;
    }
    return true;
  }

  public static <T> List<T> intersection(List<T> list1, List<T> list2) {
    List<T> list = new ArrayList<T>();

    for (T t : list1) {
      if (list2.contains(t)) {
        list.add(t);
      }
    }

    return list;
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
}
