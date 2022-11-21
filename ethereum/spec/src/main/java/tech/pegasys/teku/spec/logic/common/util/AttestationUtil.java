/*
 * Copyright ConsenSys Software Inc., 2022
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
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import com.google.common.collect.Comparators;
import it.unimi.dsi.fastutil.ints.IntList;
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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;
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
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_slashable_attestation_data</a>
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
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_indexed_attestation</a>
   */
  public IndexedAttestation getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    final List<Integer> attestingIndices =
        getAttestingIndices(state, attestation.getData(), attestation.getAggregationBits());

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
   * @param data
   * @param bits
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_attesting_indices</a>
   */
  public IntList getAttestingIndices(
      final BeaconState state, final AttestationData data, final SszBitlist bits) {
    return IntList.of(streamAttestingIndices(state, data, bits).toArray());
  }

  public IntStream streamAttestingIndices(
      final BeaconState state, final AttestationData data, final SszBitlist bits) {
    IntList committee =
        beaconStateAccessors.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        bits.size() == committee.size(),
        "Aggregation bitlist size (%s) does not match committee size (%s)",
        bits.size(),
        committee.size());
    return IntStream.range(0, committee.size()).filter(bits::getBit).map(committee::getInt);
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork, final BeaconState state, final ValidateableAttestation attestation) {
    return isValidIndexedAttestation(fork, state, attestation, BLSSignatureVerifier.SIMPLE);
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork,
      final BeaconState state,
      final ValidateableAttestation attestation,
      final BLSSignatureVerifier blsSignatureVerifier) {
    final SafeFuture<AttestationProcessingResult> result =
        isValidIndexedAttestationAsync(
            fork, state, attestation, AsyncBLSSignatureVerifier.wrap(blsSignatureVerifier));

    return result.getImmediately();
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final ValidateableAttestation attestation,
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
                attestation.saveCommitteeShufflingSeed(state);
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
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_indexed_attestation</a>
   */
  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork, final BeaconState state, final IndexedAttestation indexedAttestation) {
    return isValidIndexedAttestation(fork, state, indexedAttestation, BLSSignatureVerifier.SIMPLE);
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

    if (indices.isEmpty()
        || !Comparators.isInStrictOrder(indices.asListUnboxed(), Comparator.naturalOrder())) {
      return completedFuture(
          AttestationProcessingResult.invalid("Attesting indices are not sorted"));
    }

    final List<BLSPublicKey> pubkeys =
        indices
            .streamUnboxed()
            .flatMap(i -> beaconStateAccessors.getValidatorPubKey(state, i).stream())
            .collect(toList());
    if (pubkeys.size() < indices.size()) {
      return completedFuture(
          AttestationProcessingResult.invalid("Attesting indices include non-existent validator"));
    }

    final BLSSignature signature = indexedAttestation.getSignature();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.BEACON_ATTESTER,
            indexedAttestation.getData().getTarget().getEpoch(),
            fork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpers.computeSigningRoot(indexedAttestation.getData(), domain);

    return signatureVerifier
        .verify(pubkeys, signingRoot, signature)
        .thenApply(
            isValidSignature -> {
              if (isValidSignature) {
                return AttestationProcessingResult.SUCCESSFUL;
              } else {
                LOG.debug(
                    "AttestationUtil.is_valid_indexed_attestation: Verify aggregate signature");
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

  public abstract AttestationWorthinessChecker createAttestationWorthinessChecker(
      BeaconState state);
}
