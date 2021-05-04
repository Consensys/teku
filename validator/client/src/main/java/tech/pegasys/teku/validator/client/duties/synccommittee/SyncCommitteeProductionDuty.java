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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.duties.DutyResult;

public class SyncCommitteeProductionDuty {

  private final ChainHeadTracker chainHeadTracker;
  private final ForkProvider forkProvider;
  private final Collection<ValidatorAndCommitteeIndices> assignments;

  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;

  public SyncCommitteeProductionDuty(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final ChainHeadTracker chainHeadTracker,
      final Collection<ValidatorAndCommitteeIndices> assignments) {
    this.chainHeadTracker = chainHeadTracker;
    this.forkProvider = forkProvider;
    this.assignments = assignments;
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
  }

  public SafeFuture<DutyResult> produceSignatures(final UInt64 slot) {
    if (assignments.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider
        .getForkInfo()
        .thenCompose(
            forkInfo ->
                chainHeadTracker
                    .getCurrentChainHead(slot)
                    .thenCompose(
                        maybeBlockRoot ->
                            maybeBlockRoot
                                .map(blockRoot -> produceSignatures(forkInfo, slot, blockRoot))
                                .orElseGet(
                                    () ->
                                        SafeFuture.completedFuture(
                                            DutyResult.forError(new NodeSyncingException())))))
        .exceptionally(
            error ->
                DutyResult.forError(
                    assignments.stream()
                        .map(assignment -> assignment.getValidator().getPublicKey())
                        .collect(Collectors.toSet()),
                    error));
  }

  private SafeFuture<DutyResult> produceSignatures(
      final ForkInfo forkInfo, final UInt64 slot, final Bytes32 blockRoot) {
    return SafeFuture.collectAll(
            assignments.stream()
                .map(assignment -> produceSignature(forkInfo, slot, blockRoot, assignment)))
        .thenApply(this::sendSignatures);
  }

  private DutyResult sendSignatures(final List<ProductionResult> results) {
    final List<SyncCommitteeSignature> producedSignatures =
        results.stream().flatMap(result -> result.signature.stream()).collect(Collectors.toList());
    validatorApiChannel.sendSyncCommitteeSignatures(producedSignatures);
    return results.stream()
        .map(result -> result.result)
        .reduce(DutyResult::combine)
        .orElse(DutyResult.NO_OP);
  }

  private SafeFuture<ProductionResult> produceSignature(
      final ForkInfo forkInfo,
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment) {
    return assignment
        .getValidator()
        .getSigner()
        .signSyncCommitteeSignature(slot, blockRoot, forkInfo)
        .thenApply(
            signature ->
                new ProductionResult(
                    createSyncCommitteeSignature(slot, blockRoot, assignment, signature)))
        .exceptionally(
            error ->
                new ProductionResult(
                    DutyResult.forError(assignment.getValidator().getPublicKey(), error)));
  }

  private SyncCommitteeSignature createSyncCommitteeSignature(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment,
      final BLSSignature signature) {
    return SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions())
        .getSyncCommitteeSignatureSchema()
        .create(slot, blockRoot, UInt64.valueOf(assignment.getValidatorIndex()), signature);
  }

  private static class ProductionResult {
    private final DutyResult result;
    private final Optional<SyncCommitteeSignature> signature;

    private ProductionResult(final SyncCommitteeSignature signature) {
      this.result = DutyResult.success(signature.getBeaconBlockRoot());
      this.signature = Optional.of(signature);
    }

    private ProductionResult(final DutyResult result) {
      this.result = result;
      this.signature = Optional.empty();
    }
  }
}
