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

package tech.pegasys.teku.validator.client.duties.attestations;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.CREATE_TOTAL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

public class PayloadAttestationDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final SendingStrategy<PayloadAttestationMessage> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  private final List<ValidatorWithIndex> validators = new ArrayList<>();

  public PayloadAttestationDuty(
      final Spec spec,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final SendingStrategy<PayloadAttestationMessage> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.spec = spec;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  public void addValidator(final Validator validator, final int index) {
    validators.add(new ValidatorWithIndex(validator, index));
  }

  @Override
  public DutyType getType() {
    return DutyType.PAYLOAD_ATTESTATION_PRODUCTION;
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.info("Creating payload attestation at slot {}", slot);

    if (validators.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }

    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo -> sendingStrategy.send(produceAllAttestations(slot, forkInfo, validators)));
  }

  private Stream<SafeFuture<ProductionResult<PayloadAttestationMessage>>> produceAllAttestations(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final List<ValidatorWithIndex> validatorsWithIndex) {

    final SafeFuture<Optional<PayloadAttestationData>> unsignedAttestationFuture =
        validatorDutyMetrics.record(
            () -> validatorApiChannel.createPayloadAttestationData(slot), this, CREATE_TOTAL);

    return validatorsWithIndex.stream()
        .map(
            validatorWithIndex ->
                signAttestationForValidator(
                    slot, forkInfo, validatorWithIndex, unsignedAttestationFuture));
  }

  private SafeFuture<ProductionResult<PayloadAttestationMessage>> signAttestationForValidator(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final ValidatorWithIndex validatorWithIndex,
      final SafeFuture<Optional<PayloadAttestationData>> attestationDataFuture) {
    return attestationDataFuture
        .thenCompose(
            maybeUnsignedAttestation ->
                maybeUnsignedAttestation
                    .map(
                        attestationData -> {
                          validateAttestationData(slot, attestationData);
                          return validatorDutyMetrics.record(
                              () ->
                                  signAttestationForValidator(
                                      forkInfo, attestationData, validatorWithIndex),
                              this,
                              ValidatorDutyMetricsSteps.SIGN);
                        })
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                ProductionResult.failure(
                                    validatorWithIndex.validator.getPublicKey(),
                                    new IllegalStateException(
                                        "Unable to produce payload attestation for slot "
                                            + slot
                                            + " because chain data was unavailable")))))
        .exceptionally(
            error -> ProductionResult.failure(validatorWithIndex.validator.getPublicKey(), error));
  }

  private static void validateAttestationData(
      final UInt64 slot, final PayloadAttestationData attestationData) {
    checkArgument(
        attestationData.getSlot().equals(slot),
        "Unsigned payload attestation slot (%s) does not match expected slot %s",
        attestationData.getSlot(),
        slot);
  }

  private SafeFuture<ProductionResult<PayloadAttestationMessage>> signAttestationForValidator(
      final ForkInfo forkInfo,
      final PayloadAttestationData attestationData,
      final ValidatorWithIndex validatorWithIndex) {
    return validatorWithIndex
        .validator
        .getSigner()
        .signPayloadAttestationData(attestationData, forkInfo)
        .thenApply(
            signature ->
                createSignedPayloadAttestation(attestationData, validatorWithIndex, signature))
        .thenApply(
            attestation ->
                ProductionResult.success(
                    validatorWithIndex.validator.getPublicKey(),
                    attestationData.getBeaconBlockRoot(),
                    attestation));
  }

  private PayloadAttestationMessage createSignedPayloadAttestation(
      final PayloadAttestationData attestationData,
      final ValidatorWithIndex validatorWithIndex,
      final BLSSignature signature) {
    final PayloadAttestationMessageSchema payloadAttestationMessageSchema =
        spec.atSlot(attestationData.getSlot())
            .getSchemaDefinitions()
            .toVersionEip7732()
            .orElseThrow()
            .getPayloadAttestationMessageSchema();

    return payloadAttestationMessageSchema.create(
        UInt64.valueOf(validatorWithIndex.index), attestationData, signature);
  }

  private record ValidatorWithIndex(Validator validator, int index) {}
}
