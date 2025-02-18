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

package tech.pegasys.teku.validator.client.duties;

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
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionListSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.attestations.SendingStrategy;

public class InclusionListProductionDuty implements Duty {

  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final List<ValidatorWithIndex> validators = new ArrayList<>();
  private final SendingStrategy<SignedInclusionList> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;
  private final ValidatorApiChannel validatorApiChannel;

  public InclusionListProductionDuty(
      final Spec spec,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final SendingStrategy<SignedInclusionList> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics,
      final ValidatorApiChannel validatorApiChannel) {
    this.spec = spec;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  public DutyType getType() {
    return DutyType.INCLUSION_LIST_PRODUCTION;
  }

  public void addValidator(final Validator validator, final int validatorIndex) {
    validators.add(new ValidatorWithIndex(validator, validatorIndex));
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating inclusion lists at slot {}", slot);
    if (validators.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> sendingStrategy.send(produceAllInclusionLists(forkInfo)));
  }

  private Stream<SafeFuture<ProductionResult<SignedInclusionList>>> produceAllInclusionLists(
      final ForkInfo forkInfo) {

    return validators.stream()
        .map(
            validatorWithIndex -> {
              final SafeFuture<Optional<InclusionList>> unsignedInclusionList =
                  validatorDutyMetrics.record(
                      () ->
                          validatorApiChannel.createInclusionList(
                              slot, UInt64.valueOf(validatorWithIndex.index)),
                      this,
                      CREATE_TOTAL);
              return signAttestationForValidator(
                  slot, forkInfo, validatorWithIndex, unsignedInclusionList);
            });
  }

  private SafeFuture<ProductionResult<SignedInclusionList>> signAttestationForValidator(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final ValidatorWithIndex validatorWithIndex,
      final SafeFuture<Optional<InclusionList>> inclusionListFuture) {
    return inclusionListFuture
        .thenCompose(
            maybeUnsignedInclusionList ->
                maybeUnsignedInclusionList
                    .map(
                        inclusionList -> {
                          validationInclusionList(inclusionList);
                          return validatorDutyMetrics.record(
                              () ->
                                  signAttestationForValidator(
                                      forkInfo, inclusionList, validatorWithIndex),
                              this,
                              ValidatorDutyMetricsSteps.SIGN);
                        })
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                ProductionResult.failure(
                                    validatorWithIndex.validator.getPublicKey(),
                                    new IllegalStateException(
                                        "Unable to produce inclusion list for slot "
                                            + slot
                                            + " because chain data was unavailable")))))
        .exceptionally(
            error -> ProductionResult.failure(validatorWithIndex.validator.getPublicKey(), error));
  }

  private SafeFuture<ProductionResult<SignedInclusionList>> signAttestationForValidator(
      final ForkInfo forkInfo,
      final InclusionList inclusionList,
      final ValidatorWithIndex validatorWithIndex) {
    return validatorWithIndex
        .validator
        .getSigner()
        .signInclusionList(inclusionList, forkInfo)
        .thenApply(signature -> createSignedPayloadAttestation(inclusionList, signature))
        .thenApply(
            signedInclusionList ->
                ProductionResult.success(
                    validatorWithIndex.validator.getPublicKey(),
                    inclusionList.getInclusionListCommitteeRoot(),
                    signedInclusionList));
  }

  private SignedInclusionList createSignedPayloadAttestation(
      final InclusionList inclusionList, final BLSSignature signature) {
    final SignedInclusionListSchema signedInclusionListSchema =
        spec.atSlot(inclusionList.getSlot())
            .getSchemaDefinitions()
            .toVersionEip7805()
            .orElseThrow()
            .getSignedInclusionListSchema();

    return signedInclusionListSchema.create(inclusionList, signature);
  }

  private void validationInclusionList(final InclusionList inclusionList) {
    checkArgument(
        inclusionList.getSlot().equals(slot),
        "Unsigned inclusion list slot (%s) does not match expected slot %s",
        inclusionList.getSlot(),
        slot);
  }

  private record ValidatorWithIndex(Validator validator, int index) {}
}
