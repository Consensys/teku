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
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.MAX_INDICES_PER_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_bitfield_bit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_committee_count;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.verify_bitfield;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_crosslink_committee;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_epoch_committee_count;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_epoch_start_shard;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes.LIST_OF_BASIC;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.hash_tree_root;

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
      BeaconState headState, HashMap<BLSPublicKey, Pair<BLSKeyPair, Boolean>> validatorSet) {

    UnsignedLong slot = headState.getSlot();
    //TODO depracated method
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
    //TODO depracated method
    List<CrosslinkCommittee> crosslinkCommittees =
        BeaconStateUtil.get_crosslink_committees_at_slot(state, slot);
    Bytes32 headBlockRoot = block.signing_root("signature");
    Bytes32 crosslinkDataRoot = Bytes32.ZERO;
    UnsignedLong epochStartSlot =
        get_epoch_start_slot(BeaconStateUtil.get_current_epoch(state));
    Bytes32 epochBoundaryRoot;
    if (epochStartSlot.compareTo(slot) == 0) {
      epochBoundaryRoot = block.signing_root("signature");
    } else {
      epochBoundaryRoot = BeaconStateUtil.get_block_root(state, epochStartSlot);
    }
    UnsignedLong sourceEpoch = state.getCurrent_justified_epoch();
    Bytes32 sourceRoot = state.getCurrent_justified_root();

    // Set attestation data
    // TODO: change the generic shard number and crosslink usage to something safer
    //TODO: Crosslink constructor has changed
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
    attestationData.setCrosslink(previousCrosslink);
    return attestationData;
  }

  public static Bytes32 getAttestationMessageToSign(AttestationData attestationData) {
    AttestationDataAndCustodyBit attestation_data_and_custody_bit =
        new AttestationDataAndCustodyBit(attestationData, false);
    return attestation_data_and_custody_bit.hash_tree_root();
  }

  public static int getDomain(BeaconState state, AttestationData attestationData) {
    return get_domain(
            state.getFork(),
            BeaconStateUtil.slot_to_epoch(attestationData.getSlot()),
            Constants.DOMAIN_ATTESTATION)
        .intValue();
  }

  /**
   * Check if ``data_1`` and ``data_2`` are slashable according to Casper FFG rules.
   *
   * @param data_1
   * @param data_2
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#is_slashable_attestation_data</a>
   */
  public static boolean is_slashable_attestation_data(AttestationData data_1, AttestationData data_2){
    return (
      //case 1: double vote || case 2: surround vote
      (!data_1.equals(data_2) && data_1.getTarget_epoch().equals(data_2.getTarget_epoch()) ||
        (data_1.getSource_epoch().compareTo(data_2.getSource_epoch()) < 0 &&
                data_2.getTarget_epoch().compareTo(data_1.getTarget_epoch()) < 0))
    );
  }

  /**
   * Convert ``attestation`` to (almost) indexed-verifiable form.
   *
   * @param state
   * @param attestation
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#convert_to_indexed</a>
   */
  public static IndexedAttestation convert_to_indexed(BeaconState state, Attestation attestation){
    List<Integer> attesting_indices = get_attesting_indices(state, attestation.getData(), attestation.getAggregation_bitfield());
    List<Integer> custody_bit_1_indices = get_attesting_indices(state, attestation.getData(), attestation.getCustody_bitfield());

    List<Integer> custody_bit_0_indices = new ArrayList<Integer>();
    for(int i = 0; i < attesting_indices.size(); i++){
      Integer index = attesting_indices.get(i);
      if(!custody_bit_1_indices.contains(index))custody_bit_0_indices.add(index);
    }
    return new IndexedAttestation(
            custody_bit_0_indices,
            custody_bit_1_indices,
            attestation.getData(),
            attestation.getAggregate_signature()
            );
  }

  /**
   * Return the sorted attesting indices corresponding to ``attestation_data`` and ``bitfield``.
   *
   * @param state
   * @param attestation_data
   * @param bitfield
   * @return
   * @throws IllegalArgumentException
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_attesting_indices</a>
   */
  public static List<Integer> get_attesting_indices(
          BeaconState state, AttestationData attestation_data, Bytes bitfield) throws IllegalArgumentException {
    List<Integer> committee = get_crosslink_committee(state, attestation_data.getTarget_epoch(), attestation_data.getCrosslink().getShard());
    checkArgument(verify_bitfield(bitfield, committee.size()), "AttestationUtil.get_attesting_indices");

    List<Integer> attesting_indices = new ArrayList<Integer>();
    for(int i=0; i < committee.size(); i++){
      int index = committee.get(i).intValue();
      int bitfieldBit = get_bitfield_bit(bitfield, i);
      if((bitfieldBit & 1) == 1) attesting_indices.add(index);
    }
    return attesting_indices;
  }

  /**
   * Verify validity of ``indexed_attestation``.
   *
   * @param state
   * @param indexed_attestation
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#validate_indexed_attestation</a>
   */
  public static void validate_indexed_attestation(BeaconState state, IndexedAttestation indexed_attestation){
    List<Integer> bit_0_indices = indexed_attestation.getCustody_bit_0_indices();
    List<Integer> bit_1_indices = indexed_attestation.getCustody_bit_1_indices();

    checkArgument(bit_1_indices.size() == 0, "AttestationUtil.validate_indexed_attestation: Verify no index has custody bit equal to 1 [to be removed in phase 1]");
    checkArgument((bit_0_indices.size() + bit_1_indices.size()) <= MAX_INDICES_PER_ATTESTATION, "AttestationUtil.validate_indexed_attestation: Verify max number of indices");
    checkArgument(intersection(bit_0_indices, bit_1_indices).size() == 0, "AttestationUtil.validate_indexed_attestation: Verify index sets are disjoint");

    //Verify indices are sorted
    List<Integer> bit_0_indices_sorted = new ArrayList<Integer>(bit_0_indices);
    Collections.sort(bit_0_indices_sorted);
    List<Integer> bit_1_indices_sorted = new ArrayList<Integer>(bit_1_indices);
    Collections.sort(bit_1_indices_sorted);
    checkArgument(bit_0_indices.equals(bit_0_indices_sorted) && bit_1_indices.equals(bit_1_indices_sorted));

    List<Validator> validators = state.getValidator_registry();
    List<BLSPublicKey> pubkeys = new ArrayList<BLSPublicKey>();
    pubkeys.add(bls_aggregate_pubkeys(bit_0_indices.stream()
            .map(i -> validators.get(i).getPubkey()).collect(Collectors.toList())));
    pubkeys.add(bls_aggregate_pubkeys(bit_1_indices.stream()
            .map(i -> validators.get(i).getPubkey()).collect(Collectors.toList())));

    List<Bytes32> message_hashes = new ArrayList<Bytes32>();
    message_hashes.add(new AttestationDataAndCustodyBit(indexed_attestation.getData(), false).hash_tree_root());
    message_hashes.add(new AttestationDataAndCustodyBit(indexed_attestation.getData(), true).hash_tree_root());

    BLSSignature signature = indexed_attestation.getSignature();
    UnsignedLong domain = UnsignedLong.valueOf(get_domain(state, DOMAIN_ATTESTATION, indexed_attestation.getData().getTarget_epoch()));
    //Verify aggregate signature
    checkArgument(BLSVerify.bls_verify_multiple(pubkeys, message_hashes, signature, domain));
  }

  /**
   * Returns the data slot for the provided AttestationData
   *
   * @param state
   * @param data
   * @return
   *
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#get_attestation_data_slot</a>
   */
  public static UnsignedLong get_attestation_data_slot(BeaconState state, AttestationData data) {
    UnsignedLong committee_count = get_epoch_committee_count(state, data.getTarget_epoch());
    UnsignedLong offset = (data.getCrosslink().getShard()
            .plus(UnsignedLong.valueOf(SHARD_COUNT))
            .minus(get_epoch_start_shard(state, data.getTarget_epoch())))
              .mod(UnsignedLong.valueOf(SHARD_COUNT));
    return get_epoch_start_slot(data.getTarget_epoch()).plus(offset.dividedBy(committee_count.dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH))));
  }

  public static <T> List<T> intersection(List<T> list1, List<T> list2) {
    List<T> list = new ArrayList<T>();

    for (T t : list1) {
      if(list2.contains(t)) {
        list.add(t);
      }
    }

    return list;
  }

}
