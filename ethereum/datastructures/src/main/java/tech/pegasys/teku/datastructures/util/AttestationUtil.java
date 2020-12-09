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

package tech.pegasys.teku.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.getValidatorPubKey;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class AttestationUtil {

  private static final Logger LOG = LogManager.getLogger();

  public static Bitlist getAggregationBits(int committeeSize, int indexIntoCommittee) {
    // Create aggregation bitfield
    Bitlist aggregationBits = new Bitlist(committeeSize, MAX_VALIDATORS_PER_COMMITTEE);
    aggregationBits.setBit(indexIntoCommittee);
    return aggregationBits;
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
        SSZList.createMutable(
            attesting_indices.stream().sorted().map(UInt64::valueOf).collect(toList()),
            MAX_VALIDATORS_PER_COMMITTEE,
            UInt64.class),
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
    return stream_attesting_indices(state, data, bits).boxed().collect(toList());
  }

  public static IntStream stream_attesting_indices(
      BeaconState state, AttestationData data, Bitlist bits) {
    List<Integer> committee = get_beacon_committee(state, data.getSlot(), data.getIndex());
    checkArgument(
        bits.getCurrentSize() == committee.size(),
        "Aggregation bitlist size (%s) does not match committee size (%s)",
        bits.getCurrentSize(),
        committee.size());
    return IntStream.range(0, committee.size()).filter(bits::getBit).map(committee::get);
  }

  public static AttestationProcessingResult is_valid_indexed_attestation(
      BeaconState state, ValidateableAttestation attestation) {
    if (attestation.isValidIndexedAttestation()) {
      return AttestationProcessingResult.SUCCESSFUL;
    } else {
      try {
        IndexedAttestation indexedAttestation =
            get_indexed_attestation(state, attestation.getAttestation());
        attestation.setIndexedAttestation(indexedAttestation);
        AttestationProcessingResult result =
            is_valid_indexed_attestation(state, indexedAttestation, BLSSignatureVerifier.SIMPLE);
        if (result.isSuccessful()) {
          attestation.saveCommitteeShufflingSeed(state);
          attestation.setValidIndexedAttestation();
        }
        return result;
      } catch (IllegalArgumentException e) {
        LOG.debug("on_attestation: Attestation is not valid: ", e);
        return AttestationProcessingResult.invalid(e.getMessage());
      }
    }
  }

  /**
   * Verify validity of ``indexed_attestation``.
   *
   * @param state
   * @param indexed_attestation
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_indexed_attestation</a>
   */
  public static AttestationProcessingResult is_valid_indexed_attestation(
      BeaconState state, IndexedAttestation indexed_attestation) {
    return is_valid_indexed_attestation(state, indexed_attestation, BLSSignatureVerifier.SIMPLE);
  }

  public static AttestationProcessingResult is_valid_indexed_attestation(
      BeaconState state,
      IndexedAttestation indexed_attestation,
      BLSSignatureVerifier signatureVerifier) {
    SSZList<UInt64> indices = indexed_attestation.getAttesting_indices();

    List<UInt64> bit_0_indices_sorted = indices.stream().sorted().distinct().collect(toList());
    if (indices.isEmpty() || !indices.equals(bit_0_indices_sorted)) {
      return AttestationProcessingResult.invalid("Attesting indices are not sorted");
    }

    List<BLSPublicKey> pubkeys =
        indices.stream().flatMap(i -> getValidatorPubKey(state, i).stream()).collect(toList());
    if (pubkeys.size() < indices.size()) {
      return AttestationProcessingResult.invalid(
          "Attesting indices include non-existent validator");
    }

    BLSSignature signature = indexed_attestation.getSignature();
    Bytes32 domain =
        get_domain(
            state, DOMAIN_BEACON_ATTESTER, indexed_attestation.getData().getTarget().getEpoch());
    Bytes signing_root = compute_signing_root(indexed_attestation.getData(), domain);

    if (!signatureVerifier.verify(pubkeys, signing_root, signature)) {
      LOG.debug("AttestationUtil.is_valid_indexed_attestation: Verify aggregate signature");
      return AttestationProcessingResult.invalid("Signature is invalid");
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  // Set bits of the newAttestation on the oldBitlist
  // return true if any new bit was set
  public static boolean setBitsForNewAttestation(Bitlist oldBitlist, Attestation newAttesation) {
    Bitlist newBitlist = newAttesation.getAggregation_bits();
    if (oldBitlist.getCurrentSize() != newBitlist.getCurrentSize())
      throw new UnsupportedOperationException("Attestation bitlist size's don't match");
    boolean representsNewAttester = false;
    for (int i = 0; i < oldBitlist.getCurrentSize(); i++) {
      if (newBitlist.getBit(i) && !oldBitlist.getBit(i)) {
        oldBitlist.setBit(i);
        representsNewAttester = true;
      }
    }
    return representsNewAttester;
  }

  public static boolean representsNewAttester(
      Attestation oldAttestation, Attestation newAttestation) {
    int newAttesterIndex = getAttesterIndexIntoCommittee(newAttestation);
    return !oldAttestation.getAggregation_bits().getBit(newAttesterIndex);
  }

  // Returns the index of the first attester in the Attestation
  public static int getAttesterIndexIntoCommittee(Attestation attestation) {
    Bitlist aggregationBits = attestation.getAggregation_bits();
    for (int i = 0; i < aggregationBits.getCurrentSize(); i++) {
      if (aggregationBits.getBit(i)) {
        return i;
      }
    }
    throw new UnsupportedOperationException("Attestation doesn't have any aggregation bit set");
  }

  // Returns the indices of the attesters in the Attestation
  public static List<Integer> getAttesterIndicesIntoCommittee(Bitlist aggregationBits) {
    return aggregationBits.getAllSetBits();
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public static AttestationData getGenericAttestationData(
      UInt64 slot, BeaconState state, BeaconBlockSummary block, final UInt64 committeeIndex) {
    UInt64 epoch = compute_epoch_at_slot(slot);
    // Get variables necessary that can be shared among Attestations of all validators
    Bytes32 beacon_block_root = block.getRoot();
    UInt64 start_slot = compute_start_slot_at_epoch(epoch);
    Bytes32 epoch_boundary_block_root =
        start_slot.compareTo(slot) == 0 || state.getSlot().compareTo(start_slot) <= 0
            ? block.getRoot()
            : get_block_root_at_slot(state, start_slot);
    Checkpoint source = state.getCurrent_justified_checkpoint();
    Checkpoint target = new Checkpoint(epoch, epoch_boundary_block_root);

    // Set attestation data
    return new AttestationData(slot, committeeIndex, beacon_block_root, source, target);
  }
}
