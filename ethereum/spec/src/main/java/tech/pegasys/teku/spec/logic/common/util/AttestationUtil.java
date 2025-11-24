/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public abstract class AttestationUtil {

  private static final Logger LOG = LogManager.getLogger();

  protected final SchemaDefinitions schemaDefinitions;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final MiscHelpers miscHelpers;
  protected final SpecConfig specConfig;

  public AttestationUtil(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  /**
   * Check if ``data_1`` and ``data_2`` are slashable according to Casper FFG rules.
   *
   * @param data1
   * @param data2
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_slashable_attestation_data</a>
   */
  public boolean isSlashableAttestationData(
      final AttestationData data1, final AttestationData data2) {
    return (
    // case 1: double vote || case 2: surround vote
    (!data1.equals(data2) && data1.getTarget().getEpoch().equals(data2.getTarget().getEpoch()))
        || (data1.getSource().getEpoch().compareTo(data2.getSource().getEpoch()) < 0
            && data2.getTarget().getEpoch().compareTo(data1.getTarget().getEpoch()) < 0));
  }

  /**
   * Return the indexed attestation corresponding to ``attestation``.
   *
   * @param state
   * @param attestation
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_indexed_attestation</a>
   */
  public IndexedAttestation getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    final List<Integer> attestingIndices = getAttestingIndices(state, attestation);

    final IndexedAttestationSchema indexedAttestationSchema =
        schemaDefinitions.getIndexedAttestationSchema();

    return indexedAttestationSchema.create(
        attestingIndices.stream()
            .sorted()
            .map(UInt64::valueOf)
            .collect(indexedAttestationSchema.getAttestingIndicesSchema().collectorUnboxed()),
        attestation.getData(),
        attestation.getAggregateSignature());
  }

  /**
   * Return the sorted attesting indices corresponding to ``data`` and ``bits``.
   *
   * @param state
   * @param attestation
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/beacon-chain.md#get_attesting_indices</a>
   */
  public IntList getAttestingIndices(final BeaconState state, final Attestation attestation) {
    return IntList.of(
        streamAttestingIndices(state, attestation.getData(), attestation.getAggregationBits())
            .toArray());
  }

  public IntStream streamAttestingIndices(
      final BeaconState state, final AttestationData data, final SszBitlist aggregationBits) {
    final IntList committee =
        beaconStateAccessors.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        aggregationBits.size() == committee.size(),
        "Aggregation bitlist size (%s) does not match committee size (%s)",
        aggregationBits.size(),
        committee.size());
    return IntStream.range(0, committee.size())
        .filter(aggregationBits::getBit)
        .map(committee::getInt);
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork, final BeaconState state, final ValidatableAttestation attestation) {
    return isValidIndexedAttestation(
        fork, state, attestation, specConfig.getBLSSignatureVerifier());
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork,
      final BeaconState state,
      final ValidatableAttestation attestation,
      final BLSSignatureVerifier blsSignatureVerifier) {
    final SafeFuture<AttestationProcessingResult> result =
        isValidIndexedAttestationAsync(
            fork, state, attestation, AsyncBLSSignatureVerifier.wrap(blsSignatureVerifier));

    return result.getImmediately();
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {
    if (attestation.isValidIndexedAttestation()
        && attestation.getIndexedAttestation().isPresent()) {
      return completedFuture(AttestationProcessingResult.SUCCESSFUL);
    }

    return SafeFuture.of(
            () -> {
              // getIndexedAttestation() throws, so wrap it in a future
              final IndexedAttestation indexedAttestation =
                  getIndexedAttestation(state, attestation.getAttestation());
              attestation.setIndexedAttestation(indexedAttestation);
              return indexedAttestation;
            })
        .thenCompose(
            att -> {
              if (attestation.isValidIndexedAttestation()) {
                return completedFuture(AttestationProcessingResult.SUCCESSFUL);
              }
              return isValidIndexedAttestationAsync(fork, state, att, blsSignatureVerifier);
            })
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
                attestation.setValidIndexedAttestation();
              }
              return result;
            })
        .exceptionallyCompose(
            err -> {
              if (err.getCause() instanceof IllegalArgumentException) {
                LOG.debug("on_attestation: Attestation is not valid: ", err);
                return SafeFuture.completedFuture(
                    AttestationProcessingResult.invalid(err.getMessage()));
              } else {
                return SafeFuture.failedFuture(err);
              }
            });
  }

  /**
   * Verify validity of ``indexed_attestation``.
   *
   * @param state
   * @param indexedAttestation
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_indexed_attestation</a>
   */
  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork, final BeaconState state, final IndexedAttestation indexedAttestation) {
    return isValidIndexedAttestation(
        fork, state, indexedAttestation, specConfig.getBLSSignatureVerifier());
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork,
      final BeaconState state,
      final IndexedAttestation indexedAttestation,
      final BLSSignatureVerifier signatureVerifier) {
    final SafeFuture<AttestationProcessingResult> result =
        isValidIndexedAttestationAsync(
            fork, state, indexedAttestation, AsyncBLSSignatureVerifier.wrap(signatureVerifier));

    return result.getImmediately();
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final IndexedAttestation indexedAttestation,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final SszUInt64List indices = indexedAttestation.getAttestingIndices();

    if (indices.isEmpty()) {
      return completedFuture(
          AttestationProcessingResult.invalid("Attesting indices must not be empty"));
    }

    UInt64 lastIndex = null;
    final List<BLSPublicKey> pubkeys = new ArrayList<>(indices.size());

    for (final UInt64 index : indices.asListUnboxed()) {
      if (lastIndex != null && index.isLessThanOrEqualTo(lastIndex)) {
        return completedFuture(
            AttestationProcessingResult.invalid("Attesting indices are not sorted"));
      }
      lastIndex = index;
      final Optional<BLSPublicKey> validatorPubKey =
          beaconStateAccessors.getValidatorPubKey(state, index);
      if (validatorPubKey.isEmpty()) {
        return completedFuture(
            AttestationProcessingResult.invalid(
                "Attesting indices include non-existent validator"));
      }
      pubkeys.add(validatorPubKey.get());
    }

    return validateAttestationDataSignature(
        fork,
        state,
        Collections.unmodifiableList(pubkeys),
        indexedAttestation.getSignature(),
        indexedAttestation.getData(),
        signatureVerifier);
  }

  protected SafeFuture<AttestationProcessingResult> validateAttestationDataSignature(
      final Fork fork,
      final BeaconState state,
      final List<BLSPublicKey> publicKeys,
      final BLSSignature signature,
      final AttestationData attestationData,
      final AsyncBLSSignatureVerifier signatureVerifier) {

    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.BEACON_ATTESTER,
            attestationData.getTarget().getEpoch(),
            fork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpers.computeSigningRoot(attestationData, domain);

    return signatureVerifier
        .verify(publicKeys, signingRoot, signature)
        .thenApply(
            isValidSignature -> {
              if (isValidSignature) {
                return AttestationProcessingResult.SUCCESSFUL;
              } else {
                LOG.debug("AttestationUtil.validateAttestationDataSignature: Verify signature");
                return AttestationProcessingResult.invalid("Signature is invalid");
              }
            });
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    // Get variables necessary that can be shared among Attestations of all validators
    final Bytes32 beaconBlockRoot = block.getRoot();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    final Bytes32 epochBoundaryBlockRoot =
        startSlot.compareTo(slot) == 0 || state.getSlot().compareTo(startSlot) <= 0
            ? block.getRoot()
            : beaconStateAccessors.getBlockRootAtSlot(state, startSlot);
    final Checkpoint source = state.getCurrentJustifiedCheckpoint();
    final Checkpoint target = new Checkpoint(epoch, epochBoundaryBlockRoot);

    // Set attestation data
    return new AttestationData(slot, committeeIndex, beaconBlockRoot, source, target);
  }

  public abstract Optional<SlotInclusionGossipValidationResult>
      performSlotInclusionGossipValidation(
          Attestation attestation, UInt64 genesisTime, UInt64 currentTimeMillis);

  public abstract Attestation convertSingleAttestationToAggregated(
      final BeaconState state, final SingleAttestation singleAttestation);

  public abstract AttestationValidationResult validateIndexValue(final UInt64 index);

  public abstract AttestationValidationResult validatePayloadStatus(
      final AttestationData attestationData, final Optional<UInt64> maybeBlockSlot);

  public enum SlotInclusionGossipValidationResult {
    IGNORE,
    SAVE_FOR_FUTURE
  }
}
