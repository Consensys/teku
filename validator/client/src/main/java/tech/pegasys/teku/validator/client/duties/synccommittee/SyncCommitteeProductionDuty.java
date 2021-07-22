/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.validator.api.SubmitCommitteeMessageError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.duties.DutyResult;

public class SyncCommitteeProductionDuty {
  private static final Logger LOG = LogManager.getLogger();
  private final ForkProvider forkProvider;
  private final Collection<ValidatorAndCommitteeIndices> assignments;

  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;

  public SyncCommitteeProductionDuty(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final Collection<ValidatorAndCommitteeIndices> assignments) {
    this.forkProvider = forkProvider;
    this.assignments = assignments;
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
  }

  public SafeFuture<DutyResult> produceMessages(final UInt64 slot, final Bytes32 blockRoot) {
    if (assignments.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> produceMessages(forkInfo, slot, blockRoot))
        .exceptionally(
            error ->
                DutyResult.forError(
                    assignments.stream()
                        .map(assignment -> assignment.getValidator().getPublicKey())
                        .collect(Collectors.toSet()),
                    error));
  }

  private SafeFuture<DutyResult> produceMessages(
      final ForkInfo forkInfo, final UInt64 slot, final Bytes32 blockRoot) {
    return SafeFuture.collectAll(
            assignments.stream()
                .map(assignment -> produceMessage(forkInfo, slot, blockRoot, assignment)))
        .thenCompose(this::sendSignatures);
  }

  private SafeFuture<DutyResult> sendSignatures(final List<ProductionResult> results) {
    // Split into results that produced a signature vs those that failed already
    final List<ProductionResult> signatureCreated =
        results.stream().filter(result -> result.message.isPresent()).collect(toList());
    final DutyResult combinedFailures =
        combineResults(
            results.stream().filter(result -> result.message.isEmpty()).collect(toList()));

    if (signatureCreated.isEmpty()) {
      return SafeFuture.completedFuture(combinedFailures);
    }

    return validatorApiChannel
        .sendSyncCommitteeMessages(
            signatureCreated.stream().map(result -> result.message.orElseThrow()).collect(toList()))
        .thenApply(
            errors -> {
              errors.forEach(error -> replaceResult(signatureCreated, error));
              return combineResults(signatureCreated).combine(combinedFailures);
            });
  }

  private DutyResult combineResults(final List<ProductionResult> results) {
    return results.stream()
        .map(result -> result.result)
        .reduce(DutyResult::combine)
        .orElse(DutyResult.NO_OP);
  }

  private void replaceResult(
      final List<ProductionResult> sentResults, final SubmitCommitteeMessageError error) {
    if (error.getIndex().isGreaterThanOrEqualTo(sentResults.size())) {
      LOG.error(
          "Beacon node reported an error sending sync committee message at index {} with message '{}' but only {} messages were sent",
          error.getIndex(),
          error.getMessage(),
          sentResults.size());
      return;
    }
    final int index = error.getIndex().intValue();
    final ProductionResult originalResult = sentResults.get(index);
    sentResults.set(
        index,
        new ProductionResult(
            originalResult.validatorPublicKey,
            DutyResult.forError(
                originalResult.validatorPublicKey,
                new RestApiReportedException(error.getMessage()))));
  }

  private SafeFuture<ProductionResult> produceMessage(
      final ForkInfo forkInfo,
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment) {
    final BLSPublicKey validatorPublicKey = assignment.getValidator().getPublicKey();
    return assignment
        .getValidator()
        .getSigner()
        .signSyncCommitteeMessage(slot, blockRoot, forkInfo)
        .thenApply(
            signature ->
                new ProductionResult(
                    validatorPublicKey,
                    createSyncCommitteeMessage(slot, blockRoot, assignment, signature)))
        .exceptionally(
            error ->
                new ProductionResult(
                    validatorPublicKey, DutyResult.forError(validatorPublicKey, error)));
  }

  private SyncCommitteeMessage createSyncCommitteeMessage(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment,
      final BLSSignature signature) {
    return SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions())
        .getSyncCommitteeMessageSchema()
        .create(slot, blockRoot, UInt64.valueOf(assignment.getValidatorIndex()), signature);
  }

  private static class ProductionResult {
    private final BLSPublicKey validatorPublicKey;
    private final DutyResult result;
    private final Optional<SyncCommitteeMessage> message;

    private ProductionResult(
        final BLSPublicKey validatorPublicKey, final SyncCommitteeMessage message) {
      this.validatorPublicKey = validatorPublicKey;
      this.result = DutyResult.success(message.getBeaconBlockRoot());
      this.message = Optional.of(message);
    }

    private ProductionResult(final BLSPublicKey validatorPublicKey, final DutyResult result) {
      this.validatorPublicKey = validatorPublicKey;
      this.result = result;
      this.message = Optional.empty();
    }
  }

  private static class RestApiReportedException extends Exception {
    public RestApiReportedException(final String message) {
      super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      // Stack trace is meaningless as the rejection started in the beacon node so don't fill in
      return this;
    }
  }
}
