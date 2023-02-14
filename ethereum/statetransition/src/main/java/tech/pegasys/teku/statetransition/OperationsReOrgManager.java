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

package tech.pegasys.teku.statetransition;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

public class OperationsReOrgManager implements ChainHeadChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final OperationPool<SignedVoluntaryExit> exitPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final AttestationManager attestationManager;
  private final AggregatingAttestationPool attestationPool;
  private final BlsToExecutionOperationPool blsToExecutionOperationPool;
  private final RecentChainData recentChainData;

  public OperationsReOrgManager(
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<SignedVoluntaryExit> exitPool,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final BlsToExecutionOperationPool blsToExecutionOperationPool,
      final RecentChainData recentChainData) {
    this.exitPool = exitPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.attestationManager = attestationManager;
    this.attestationPool = attestationPool;
    this.blsToExecutionOperationPool = blsToExecutionOperationPool;
    this.recentChainData = recentChainData;
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ReorgContext> optionalReorgContext) {
    optionalReorgContext.ifPresent(
        reorgContext -> {
          final NavigableMap<UInt64, Bytes32> notCanonicalBlockRoots =
              recentChainData.getAncestorsOnFork(
                  reorgContext.getCommonAncestorSlot(), reorgContext.getOldBestBlockRoot());
          final NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots =
              recentChainData.getAncestorsOnFork(
                  reorgContext.getCommonAncestorSlot(), bestBlockRoot);

          if (!notCanonicalBlockRoots.isEmpty()) {
            attestationPool.onReorg(reorgContext.getCommonAncestorSlot());
          }
          processNonCanonicalBlockOperations(notCanonicalBlockRoots.values());
          processCanonicalBlockOperations(nowCanonicalBlockRoots.values());
        });
  }

  private void processNonCanonicalBlockOperations(
      final Collection<Bytes32> nonCanonicalBlockRoots) {
    nonCanonicalBlockRoots.forEach(
        root -> {
          final SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
              recentChainData.retrieveBlockByRoot(root);
          maybeBlockFuture
              .thenAccept(
                  maybeBlock ->
                      maybeBlock.ifPresentOrElse(
                          block -> {
                            final BeaconBlockBody blockBody = block.getBody();
                            proposerSlashingPool.addAll(blockBody.getProposerSlashings());
                            attesterSlashingPool.addAll(blockBody.getAttesterSlashings());
                            exitPool.addAll(blockBody.getVoluntaryExits());
                            blockBody
                                .getOptionalBlsToExecutionChanges()
                                .ifPresent(blsToExecutionOperationPool::addAll);

                            processNonCanonicalBlockAttestations(blockBody.getAttestations(), root);
                          },
                          () ->
                              LOG.debug(
                                  "Failed to re-queue operations for now non-canonical block: {}",
                                  root)))
              .finish(
                  err ->
                      LOG.warn(
                          "Failed to re-queue operations for now non-canonical block: {}",
                          root,
                          err));
        });
  }

  private void processNonCanonicalBlockAttestations(
      final Iterable<Attestation> attestations, final Bytes32 blockRoot) {
    // Attestations need to get re-processed through AttestationManager
    // because we don't have access to the state with which they were
    // verified anymore and we need to make sure later on
    // that they're being included on the correct fork.
    attestations.forEach(
        attestation ->
            attestationManager
                .onAttestation(
                    ValidateableAttestation.fromReorgedBlock(
                        recentChainData.getSpec(), attestation))
                .finish(
                    result ->
                        result.ifInvalid(
                            reason ->
                                LOG.debug(
                                    "Rejected re-queued attestation from block: {} due to: {}",
                                    blockRoot,
                                    reason)),
                    err ->
                        LOG.error(
                            "Failed to process re-queued attestation from block: {}",
                            blockRoot,
                            err)));
  }

  private void processCanonicalBlockOperations(final Collection<Bytes32> canonicalBlockRoots) {
    canonicalBlockRoots.forEach(
        root -> {
          final SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
              recentChainData.retrieveBlockByRoot(root);
          maybeBlockFuture
              .thenAccept(
                  maybeBlock ->
                      maybeBlock.ifPresentOrElse(
                          block -> {
                            final BeaconBlockBody blockBody = block.getBody();
                            proposerSlashingPool.removeAll(blockBody.getProposerSlashings());
                            attesterSlashingPool.removeAll(blockBody.getAttesterSlashings());
                            exitPool.removeAll(blockBody.getVoluntaryExits());
                            attestationPool.onAttestationsIncludedInBlock(
                                block.getSlot(), blockBody.getAttestations());
                            blockBody
                                .getOptionalBlsToExecutionChanges()
                                .ifPresent(blsToExecutionOperationPool::removeAll);
                          },
                          () ->
                              LOG.debug(
                                  "Failed to remove operations from pools for now canonical block: {}",
                                  root)))
              .finish(
                  err ->
                      LOG.warn(
                          "Failed to remove operations from pools for now canonical block: {}",
                          root,
                          err));
        });
  }
}
