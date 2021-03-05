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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Comparators;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;
import tech.pegasys.teku.ssz.backing.collections.SszUInt64List;

public class AttestationUtil {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConstants specConstants;
  private final BeaconStateUtil beaconStateUtil;
  private final ValidatorsUtil validatorsUtil;

  public AttestationUtil(
      final SpecConstants specConstants,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorsUtil validatorsUtil) {
    this.specConstants = specConstants;
    this.beaconStateUtil = beaconStateUtil;
    this.validatorsUtil = validatorsUtil;
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
  public boolean isSlashableAttestationData(AttestationData data_1, AttestationData data_2) {
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
  public IndexedAttestation getIndexedAttestation(BeaconState state, Attestation attestation) {
    List<Integer> attesting_indices =
        getAttestingIndices(state, attestation.getData(), attestation.getAggregation_bits());

    return new IndexedAttestation(
        attesting_indices.stream()
            .sorted()
            .map(UInt64::valueOf)
            .collect(IndexedAttestation.SSZ_SCHEMA.getAttestingIndicesSchema().collectorUnboxed()),
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
  public List<Integer> getAttestingIndices(
      BeaconState state, AttestationData data, SszBitlist bits) {
    return streamAttestingIndices(state, data, bits).boxed().collect(toList());
  }

  public IntStream streamAttestingIndices(
      BeaconState state, AttestationData data, SszBitlist bits) {
    List<Integer> committee =
        beaconStateUtil.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        bits.size() == committee.size(),
        "Aggregation bitlist size (%s) does not match committee size (%s)",
        bits.size(),
        committee.size());
    return IntStream.range(0, committee.size()).filter(bits::getBit).map(committee::get);
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      BeaconState state, ValidateableAttestation attestation) {
    if (attestation.isValidIndexedAttestation()) {
      return AttestationProcessingResult.SUCCESSFUL;
    } else {
      try {
        IndexedAttestation indexedAttestation =
            getIndexedAttestation(state, attestation.getAttestation());
        attestation.setIndexedAttestation(indexedAttestation);
        AttestationProcessingResult result =
            isValidIndexedAttestation(state, indexedAttestation, BLSSignatureVerifier.SIMPLE);
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
  public AttestationProcessingResult isValidIndexedAttestation(
      BeaconState state, IndexedAttestation indexed_attestation) {
    return isValidIndexedAttestation(state, indexed_attestation, BLSSignatureVerifier.SIMPLE);
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      BeaconState state,
      IndexedAttestation indexed_attestation,
      BLSSignatureVerifier signatureVerifier) {
    SszUInt64List indices = indexed_attestation.getAttesting_indices();

    if (indices.isEmpty()
        || !Comparators.isInStrictOrder(indices.asListUnboxed(), Comparator.naturalOrder())) {
      return AttestationProcessingResult.invalid("Attesting indices are not sorted");
    }

    List<BLSPublicKey> pubkeys =
        indices
            .streamUnboxed()
            .flatMap(i -> validatorsUtil.getValidatorPubKey(state, i).stream())
            .collect(toList());
    if (pubkeys.size() < indices.size()) {
      return AttestationProcessingResult.invalid(
          "Attesting indices include non-existent validator");
    }

    BLSSignature signature = indexed_attestation.getSignature();
    Bytes32 domain =
        beaconStateUtil.getDomain(
            state,
            specConstants.getDomainBeaconAttester(),
            indexed_attestation.getData().getTarget().getEpoch());
    Bytes signing_root = beaconStateUtil.computeSigningRoot(indexed_attestation.getData(), domain);

    if (!signatureVerifier.verify(pubkeys, signing_root, signature)) {
      LOG.debug("AttestationUtil.is_valid_indexed_attestation: Verify aggregate signature");
      return AttestationProcessingResult.invalid("Signature is invalid");
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  public boolean representsNewAttester(Attestation oldAttestation, Attestation newAttestation) {
    int newAttesterIndex = getAttesterIndexIntoCommittee(newAttestation);
    return !oldAttestation.getAggregation_bits().getBit(newAttesterIndex);
  }

  // Returns the index of the first attester in the Attestation
  public int getAttesterIndexIntoCommittee(Attestation attestation) {
    SszBitlist aggregationBits = attestation.getAggregation_bits();
    for (int i = 0; i < aggregationBits.size(); i++) {
      if (aggregationBits.getBit(i)) {
        return i;
      }
    }
    throw new UnsupportedOperationException("Attestation doesn't have any aggregation bit set");
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public AttestationData getGenericAttestationData(
      UInt64 slot, BeaconState state, BeaconBlockSummary block, final UInt64 committeeIndex) {
    UInt64 epoch = beaconStateUtil.computeEpochAtSlot(slot);
    // Get variables necessary that can be shared among Attestations of all validators
    Bytes32 beacon_block_root = block.getRoot();
    UInt64 start_slot = beaconStateUtil.computeStartSlotAtEpoch(epoch);
    Bytes32 epoch_boundary_block_root =
        start_slot.compareTo(slot) == 0 || state.getSlot().compareTo(start_slot) <= 0
            ? block.getRoot()
            : beaconStateUtil.getBlockRootAtSlot(state, start_slot);
    Checkpoint source = state.getCurrent_justified_checkpoint();
    Checkpoint target = new Checkpoint(epoch, epoch_boundary_block_root);

    // Set attestation data
    return new AttestationData(slot, committeeIndex, beacon_block_root, source, target);
  }
}
