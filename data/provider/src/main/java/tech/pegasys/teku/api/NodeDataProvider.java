/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api;

import static tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache.TRACKED_EPOCHS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.request.v1.validator.ValidatorLivenessRequest;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.response.v1.validator.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.blocks.ReceivedBlockListener;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;

public class NodeDataProvider {

  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing>
      attesterSlashingPool;
  private final OperationPool<tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing>
      proposerSlashingPool;
  private final OperationPool<tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit>
      voluntaryExitPool;
  private final SyncCommitteeContributionPool syncCommitteeContributionPool;
  private final BlockManager blockManager;
  private final AttestationManager attestationManager;
  private final ActiveValidatorChannel activeValidatorChannel;
  private final boolean isLivenessTrackingEnabled;

  public NodeDataProvider(
      AggregatingAttestationPool attestationPool,
      OperationPool<tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing>
          attesterSlashingsPool,
      OperationPool<tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing>
          proposerSlashingPool,
      OperationPool<tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit>
          voluntaryExitPool,
      final SyncCommitteeContributionPool syncCommitteeContributionPool,
      BlockManager blockManager,
      AttestationManager attestationManager,
      final boolean isLivenessTrackingEnabled,
      final ActiveValidatorChannel activeValidatorChannel) {
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingsPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.syncCommitteeContributionPool = syncCommitteeContributionPool;
    this.blockManager = blockManager;
    this.attestationManager = attestationManager;
    this.activeValidatorChannel = activeValidatorChannel;
    this.isLivenessTrackingEnabled = isLivenessTrackingEnabled;
  }

  public List<Attestation> getAttestations(
      Optional<UInt64> maybeSlot, Optional<UInt64> maybeCommitteeIndex) {
    return attestationPool
        .getAttestations(maybeSlot, maybeCommitteeIndex)
        .map(Attestation::new)
        .collect(Collectors.toList());
  }

  public List<AttesterSlashing> getAttesterSlashings() {
    return attesterSlashingPool.getAll().stream()
        .map(AttesterSlashing::new)
        .collect(Collectors.toList());
  }

  public List<ProposerSlashing> getProposerSlashings() {
    return proposerSlashingPool.getAll().stream()
        .map(ProposerSlashing::new)
        .collect(Collectors.toList());
  }

  public List<SignedVoluntaryExit> getVoluntaryExits() {
    return voluntaryExitPool.getAll().stream()
        .map(SignedVoluntaryExit::new)
        .collect(Collectors.toList());
  }

  public SafeFuture<InternalValidationResult> postVoluntaryExit(SignedVoluntaryExit exit) {
    return voluntaryExitPool.add(exit.asInternalSignedVoluntaryExit());
  }

  public SafeFuture<InternalValidationResult> postAttesterSlashing(AttesterSlashing slashing) {
    return attesterSlashingPool.add(slashing.asInternalAttesterSlashing());
  }

  public SafeFuture<InternalValidationResult> postProposerSlashing(ProposerSlashing slashing) {
    return proposerSlashingPool.add(slashing.asInternalProposerSlashing());
  }

  public void subscribeToReceivedBlocks(ReceivedBlockListener listener) {
    blockManager.subscribeToReceivedBlocks(listener);
  }

  public void subscribeToValidAttestations(ProcessedAttestationListener listener) {
    attestationManager.subscribeToAllValidAttestations(listener);
  }

  public void subscribeToNewVoluntaryExits(
      OperationPool.OperationAddedSubscriber<
              tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit>
          listener) {
    voluntaryExitPool.subscribeOperationAdded(listener);
  }

  public void subscribeToSyncCommitteeContributions(
      OperationPool.OperationAddedSubscriber<SignedContributionAndProof> listener) {
    syncCommitteeContributionPool.subscribeOperationAdded(listener);
  }

  public SafeFuture<Optional<PostValidatorLivenessResponse>> getValidatorLiveness(
      final ValidatorLivenessRequest request, final Optional<UInt64> maybeCurrentEpoch) {
    if (!isLivenessTrackingEnabled) {
      return SafeFuture.failedFuture(
          new BadRequestException(
              "Validator liveness tracking is not enabled on this beacon node, cannot service request"));
    }
    // if no validator indices were requested, that's a bad request.
    if (request.indices.isEmpty()) {
      return SafeFuture.failedFuture(
          new BadRequestException("No validator indices posted in validator liveness request"));
    }
    if (maybeCurrentEpoch.isEmpty()) {
      return SafeFuture.failedFuture(new ServiceUnavailableException());
    }

    final UInt64 currentEpoch = maybeCurrentEpoch.get();
    if (currentEpoch.isLessThan(request.epoch)) {
      return SafeFuture.failedFuture(
          new BadRequestException(
              String.format(
                  "Current node epoch %s, cannot check liveness for a future epoch %s",
                  currentEpoch, request.epoch)));
    } else if (currentEpoch.minusMinZero(TRACKED_EPOCHS).isGreaterThan(request.epoch)) {
      return SafeFuture.failedFuture(
          new BadRequestException(
              String.format(
                  "Current node epoch %s, cannot check liveness for an epoch (%s) more than %d in the past",
                  currentEpoch, request.epoch, TRACKED_EPOCHS)));
    }

    return activeValidatorChannel
        .validatorsLiveAtEpoch(request.indices, request.epoch)
        .thenApply(
            validatorLivenessMap -> {
              final List<ValidatorLivenessAtEpoch> livenessAtEpochs = new ArrayList<>();
              validatorLivenessMap.forEach(
                  (validatorIndex, liveness) ->
                      livenessAtEpochs.add(
                          new ValidatorLivenessAtEpoch(validatorIndex, request.epoch, liveness)));
              return Optional.of(new PostValidatorLivenessResponse(livenessAtEpochs));
            });
  }
}
