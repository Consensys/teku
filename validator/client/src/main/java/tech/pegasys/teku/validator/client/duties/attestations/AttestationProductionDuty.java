/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.client.duties.attestations;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.CREATE_TOTAL;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Int2ObjectMap<ScheduledCommittee> validatorsByCommitteeIndex =
      new Int2ObjectOpenHashMap<>();
  private final Spec spec;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final SendingStrategy<Attestation> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  public AttestationProductionDuty(
      final Spec spec,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final SendingStrategy<Attestation> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.spec = spec;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  @Override
  public DutyType getType() {
    return DutyType.ATTESTATION_PRODUCTION;
  }

  /**
   * Adds a validator that should produce an attestation in this slot.
   *
   * @param validator the validator to produce an attestation
   * @param attestationCommitteeIndex the committee index for the validator
   * @param committeePosition the validator's position within the committee
   * @param validatorIndex the index of the validator
   * @param committeeSize the number of validators in the committee
   * @return a future which will be completed with the unsigned attestation for the committee.
   */
  public SafeFuture<Optional<AttestationData>> addValidator(
      final Validator validator,
      final int attestationCommitteeIndex,
      final int committeePosition,
      final int validatorIndex,
      final int committeeSize) {
    final ScheduledCommittee committee =
        validatorsByCommitteeIndex.computeIfAbsent(
            attestationCommitteeIndex, key -> new ScheduledCommittee());
    committee.addValidator(
        validator, attestationCommitteeIndex, committeePosition, validatorIndex, committeeSize);
    return committee.getAttestationDataFuture();
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating attestations at slot {}", slot);
    if (validatorsByCommitteeIndex.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                sendingStrategy.send(
                    produceAllAttestations(slot, forkInfo, validatorsByCommitteeIndex)));
  }

  private Stream<SafeFuture<ProductionResult<Attestation>>> produceAllAttestations(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final Int2ObjectMap<ScheduledCommittee> validatorsByCommitteeIndex) {
    return validatorsByCommitteeIndex.int2ObjectEntrySet().stream()
        .flatMap(
            entry ->
                produceAttestationsForCommittee(slot, forkInfo, entry.getIntKey(), entry.getValue())
                    .stream());
  }

  private List<SafeFuture<ProductionResult<Attestation>>> produceAttestationsForCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ScheduledCommittee committee) {
    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        validatorDutyMetrics.record(
            () -> validatorApiChannel.createAttestationData(slot, committeeIndex),
            this,
            CREATE_TOTAL);
    unsignedAttestationFuture.propagateTo(committee.getAttestationDataFuture());

    return committee.getValidators().stream()
        .map(
            validator ->
                signAttestationForValidatorInCommittee(
                    slot, forkInfo, committeeIndex, validator, unsignedAttestationFuture))
        .toList();
  }

  private SafeFuture<ProductionResult<Attestation>> signAttestationForValidatorInCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ValidatorWithAttestationDutyInfo validator,
      final SafeFuture<Optional<AttestationData>> attestationDataFuture) {
    return attestationDataFuture
        .thenCompose(
            maybeUnsignedAttestation ->
                maybeUnsignedAttestation
                    .map(
                        attestationData -> {
                          validateAttestationData(slot, attestationData);
                          return validatorDutyMetrics.record(
                              () ->
                                  signAttestationForValidator(forkInfo, attestationData, validator),
                              this,
                              ValidatorDutyMetricsSteps.SIGN);
                        })
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                ProductionResult.failure(
                                    validator.publicKey(),
                                    new IllegalStateException(
                                        "Unable to produce attestation for slot "
                                            + slot
                                            + " with committee "
                                            + committeeIndex
                                            + " because chain data was unavailable")))))
        .exceptionally(error -> ProductionResult.failure(validator.publicKey(), error));
  }

  private static void validateAttestationData(
      final UInt64 slot, final AttestationData attestationData) {
    checkArgument(
        attestationData.getSlot().equals(slot),
        "Unsigned attestation slot (%s) does not match expected slot %s",
        attestationData.getSlot(),
        slot);
  }

  private SafeFuture<ProductionResult<Attestation>> signAttestationForValidator(
      final ForkInfo forkInfo,
      final AttestationData attestationData,
      final ValidatorWithAttestationDutyInfo validator) {
    return validator
        .signer()
        .signAttestationData(attestationData, forkInfo)
        .thenApply(signature -> createSignedAttestation(attestationData, validator, signature))
        .thenApply(
            attestation ->
                ProductionResult.success(
                    validator.publicKey(), attestationData.getBeaconBlockRoot(), attestation));
  }

  private Attestation createSignedAttestation(
      final AttestationData attestationData,
      final ValidatorWithAttestationDutyInfo validator,
      final BLSSignature signature) {
    final AttestationSchema<?> attestationSchema =
        spec.atSlot(attestationData.getSlot()).getSchemaDefinitions().getAttestationSchema();
    final SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(validator.committeeSize(), validator.committeePosition());

    final Supplier<SszBitvector> committeeBitsSupplier =
        attestationSchema
            .getCommitteeBitsSchema()
            .<Supplier<SszBitvector>>map(
                committeeBitsSchema -> () -> committeeBitsSchema.ofBits(validator.committeeIndex()))
            .orElse(() -> null);
    return attestationSchema.create(
        aggregationBits, attestationData, signature, committeeBitsSupplier);
  }
}
