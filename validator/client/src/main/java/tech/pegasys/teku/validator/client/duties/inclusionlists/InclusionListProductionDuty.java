/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client.duties.inclusionlists;

import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.CREATE_TOTAL;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionListSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.SendingStrategy;

public class InclusionListProductionDuty implements Duty {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final SendingStrategy<SignedInclusionList> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  private final List<ValidatorWithDutyInfo> validators = new ArrayList<>();

  public InclusionListProductionDuty(
      final Spec spec,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final SendingStrategy<SignedInclusionList> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.spec = spec;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  @Override
  public DutyType getType() {
    return DutyType.INCLUSION_LIST_PRODUCTION;
  }

  public void addValidator(
      final Validator validator, final UInt64 validatorIndex, final Bytes32 committeeRoot) {
    validators.add(new ValidatorWithDutyInfo(validator, validatorIndex, committeeRoot));
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating inclusion lists at slot {}", slot);

    if (validators.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }

    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo -> sendingStrategy.send(produceAllInclusionLists(slot, forkInfo, validators)));
  }

  private Stream<SafeFuture<ProductionResult<SignedInclusionList>>> produceAllInclusionLists(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final List<ValidatorWithDutyInfo> validatorsWithInfo) {

    return validatorsWithInfo.stream()
        .map(
            validatorWithInfo ->
                produceInclusionListForValidator(slot, forkInfo, validatorWithInfo));
  }

  private SafeFuture<ProductionResult<SignedInclusionList>> produceInclusionListForValidator(
      final UInt64 slot, final ForkInfo forkInfo, final ValidatorWithDutyInfo validatorWithInfo) {

    final SafeFuture<ProductionResult<SignedInclusionList>> result =
        validatorDutyMetrics.record(
            () -> createAndSignInclusionList(slot, forkInfo, validatorWithInfo),
            this,
            CREATE_TOTAL);

    return result.exceptionally(
        error -> ProductionResult.failure(validatorWithInfo.validator().getPublicKey(), error));
  }

  private SafeFuture<ProductionResult<SignedInclusionList>> createAndSignInclusionList(
      final UInt64 slot, final ForkInfo forkInfo, final ValidatorWithDutyInfo validatorWithInfo) {

    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(spec.atSlot(slot).getSchemaDefinitions());
    final InclusionListSchema inclusionListSchema = schemaDefinitions.getInclusionListSchema();
    final SignedInclusionListSchema signedInclusionListSchema =
        schemaDefinitions.getSignedInclusionListSchema();

    // Create an inclusion list with empty transactions - the EL provides them, but for now
    // we create with empty list. Full implementation would call getInclusionListTransactions.
    final InclusionList inclusionList =
        inclusionListSchema.create(
            slot, validatorWithInfo.validatorIndex(), validatorWithInfo.committeeRoot(), List.of());

    return validatorDutyMetrics
        .record(
            () ->
                validatorWithInfo
                    .validator()
                    .getSigner()
                    .signInclusionList(inclusionList, forkInfo),
            this,
            ValidatorDutyMetricsSteps.SIGN)
        .thenApply(
            signature -> {
              final SignedInclusionList signed =
                  signedInclusionListSchema.create(inclusionList, signature);
              return ProductionResult.success(
                  validatorWithInfo.validator().getPublicKey(),
                  inclusionList.hashTreeRoot(),
                  signed);
            });
  }

  private record ValidatorWithDutyInfo(
      Validator validator, UInt64 validatorIndex, Bytes32 committeeRoot) {}
}
