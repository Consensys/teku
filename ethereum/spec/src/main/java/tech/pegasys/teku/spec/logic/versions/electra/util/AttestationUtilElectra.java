/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
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
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.deneb.util.AttestationUtilDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilElectra extends AttestationUtilDeneb {
  private static final Logger LOG = LogManager.getLogger();

  public AttestationUtilElectra(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  /**
   * Return the attesting indices corresponding to ``aggregation_bits`` and ``committee_bits``.
   *
   * @param state
   * @param attestation
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/dev/specs/electra/beacon-chain.md#modified-get_attesting_indices</a>
   */
  @Override
  public IntList getAttestingIndices(final BeaconState state, final Attestation attestation) {
    final List<UInt64> committeeIndices = attestation.getCommitteeIndicesRequired();
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final IntList attestingIndices = new IntArrayList();
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : committeeIndices) {
      final IntList committee =
          beaconStateAccessors.getBeaconCommittee(
              state, attestation.getData().getSlot(), committeeIndex);
      final IntList committeeAttesters =
          getCommitteeAttesters(committee, aggregationBits, committeeOffset);
      attestingIndices.addAll(committeeAttesters);
      committeeOffset += committee.size();
    }
    return attestingIndices;
  }

  public IntList getCommitteeAttesters(
      final IntList committee, final SszBitlist aggregationBits, final int committeeOffset) {
    return IntList.of(
        streamCommitteeAttesters(committee, aggregationBits, committeeOffset).toArray());
  }

  public IntStream streamCommitteeAttesters(
      final IntList committee, final SszBitlist aggregationBits, final int committeeOffset) {
    return IntStream.range(committeeOffset, committeeOffset + committee.size())
        .filter(aggregationBits::isSet)
        .map(attesterIndex -> committee.getInt(attesterIndex - committeeOffset));
  }

  /**
   * In electra, attestationData must have committee index set to 0
   *
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/dev/specs/electra/validator.md#construct-attestation</a>
   */
  @Override
  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    return super.getGenericAttestationData(slot, state, block, UInt64.ZERO);
  }

  @Override
  public IndexedAttestation getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    if (attestation.isSingleAttestation()) {
      final IndexedAttestationSchema<?> indexedAttestationSchema =
          schemaDefinitions.getIndexedAttestationSchema();

      return indexedAttestationSchema.create(
          indexedAttestationSchema
              .getAttestingIndicesSchema()
              .of(attestation.getValidatorIndex().orElseThrow()),
          attestation.getData(),
          attestation.getAggregateSignature());
    }
    return super.getIndexedAttestation(state, attestation);
  }

  @Override
  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {

    if (!attestation.getAttestation().isSingleAttestation()) {
      return super.isValidIndexedAttestationAsync(fork, state, attestation, blsSignatureVerifier);
    }

    // single attestation flow

    // 1. verify signature first
    // 2. verify call getSingleAttestationAggregationBits which also validates the validatorIndex
    // and the committee against the state
    // 3. set the indexed attestation into ValidatableAttestation and mark it as valid
    // 4. convert attestation inside ValidatableAttestation to AttestationElectra

    return isValidSingleAttestation(
            fork, state, (SingleAttestation) attestation.getAttestation(), blsSignatureVerifier)
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                final IndexedAttestation indexedAttestation =
                    getIndexedAttestation(state, attestation.getAttestation());
                attestation.setIndexedAttestation(indexedAttestation);
                attestation.setValidIndexedAttestation();
                final SszBitlist singleAttestationAggregationBits =
                    getSingleAttestationAggregationBits(state, attestation.getAttestation());
                attestation.convertFromSingleAttestation(singleAttestationAggregationBits);
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

  private SafeFuture<AttestationProcessingResult> isValidSingleAttestation(
      final Fork fork,
      final BeaconState state,
      final SingleAttestation singleAttestation,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final Optional<BLSPublicKey> pubkey =
        beaconStateAccessors.getValidatorPubKey(
            state, singleAttestation.getValidatorIndex().orElseThrow());

    if (pubkey.isEmpty()) {
      return completedFuture(
          AttestationProcessingResult.invalid("Attesting index include non-existent validator"));
    }

    final BLSSignature signature = singleAttestation.getAggregateSignature();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.BEACON_ATTESTER,
            singleAttestation.getData().getTarget().getEpoch(),
            fork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpers.computeSigningRoot(singleAttestation.getData(), domain);

    return signatureVerifier
        .verify(pubkey.get(), signingRoot, signature)
        .thenApply(
            isValidSignature -> {
              if (isValidSignature) {
                return AttestationProcessingResult.SUCCESSFUL;
              } else {
                return AttestationProcessingResult.invalid("Signature is invalid");
              }
            });
  }

  private SszBitlist getSingleAttestationAggregationBits(
      final BeaconState state, final Attestation attestation) {
    checkArgument(attestation.isSingleAttestation(), "Expecting single attestation");

    final IntList committee =
        beaconStateAccessors.getBeaconCommittee(
            state, attestation.getData().getSlot(), attestation.getFirstCommitteeIndex());

    int validatorIndex = attestation.getValidatorIndex().orElseThrow().intValue();

    int validatorCommitteeBit = committee.indexOf(validatorIndex);

    checkArgument(
        validatorCommitteeBit >= 0,
        "Validator index %s is not part of the committee %s",
        validatorIndex,
        attestation.getFirstCommitteeIndex());

    return attestation.getSchema().createAggregationBitsOf(validatorCommitteeBit);
  }
}
