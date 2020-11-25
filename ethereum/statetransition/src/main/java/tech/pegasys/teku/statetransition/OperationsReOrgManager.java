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

package tech.pegasys.teku.statetransition;

import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

public class OperationsReOrgManager implements ChainHeadChannel {
  private static final Logger LOG = LogManager.getLogger();

  final OperationPool<SignedVoluntaryExit> exitPool;
  final OperationPool<ProposerSlashing> proposerSlashingPool;
  final OperationPool<AttesterSlashing> attesterSlashingPool;
  final AttestationManager attestationManager;
  final AggregatingAttestationPool attestationPool;
  final RecentChainData recentChainData;

  public OperationsReOrgManager(
      OperationPool<ProposerSlashing> proposerSlashingPool,
      OperationPool<AttesterSlashing> attesterSlashingPool,
      OperationPool<SignedVoluntaryExit> exitPool,
      AggregatingAttestationPool attestationPool,
      AttestationManager attestationManager,
      RecentChainData recentChainData) {
    this.exitPool = exitPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.attestationManager = attestationManager;
    this.attestationPool = attestationPool;
    this.recentChainData = recentChainData;
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ReorgContext> optionalReorgContext) {
    optionalReorgContext.ifPresent(
        reorgContext -> {
          NavigableMap<UInt64, Bytes32> notCanonicalBlockRoots =
              recentChainData.getAncestorsOnFork(
                  reorgContext.getCommonAncestorSlot(), reorgContext.getOldBestBlockRoot());
          NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots =
              recentChainData.getAncestorsOnFork(
                  reorgContext.getCommonAncestorSlot(), bestBlockRoot);

          processNonCanonicalBlockOperations(notCanonicalBlockRoots.values());
          processCanonicalBlockOperations(nowCanonicalBlockRoots.values());
        });
  }

  private void processNonCanonicalBlockOperations(Collection<Bytes32> nonCanonicalBlockRoots) {
    nonCanonicalBlockRoots.forEach(
        root -> {
          SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
              recentChainData.retrieveBlockByRoot(root);
          maybeBlockFuture
              .thenAccept(
                  maybeBlock ->
                      maybeBlock.ifPresentOrElse(
                          block -> {
                            BeaconBlockBody blockBody = block.getBody();
                            proposerSlashingPool.addAll(blockBody.getProposer_slashings());
                            attesterSlashingPool.addAll(blockBody.getAttester_slashings());
                            exitPool.addAll(blockBody.getVoluntary_exits());

                            processNonCanonicalBlockAttestations(
                                blockBody.getAttestations().asList(), root);
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
      List<Attestation> attestations, Bytes32 blockRoot) {
    // Attestations need to get re-processed through AttestationManager
    // because we don't have access to the state with which they were
    // verified anymore and we need to make sure later on
    // that they're being included on the correct fork.
    attestations.forEach(
        attestation -> {
          attestationManager
              .onAttestation(ValidateableAttestation.from(attestation))
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
                          err));
        });
  }

  private void processCanonicalBlockOperations(Collection<Bytes32> canonicalBlockRoots) {
    canonicalBlockRoots.forEach(
        root -> {
          SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
              recentChainData.retrieveBlockByRoot(root);
          maybeBlockFuture
              .thenAccept(
                  maybeBlock ->
                      maybeBlock.ifPresentOrElse(
                          block -> {
                            BeaconBlockBody blockBody = block.getBody();
                            proposerSlashingPool.removeAll(blockBody.getProposer_slashings());
                            attesterSlashingPool.removeAll(blockBody.getAttester_slashings());
                            exitPool.removeAll(blockBody.getVoluntary_exits());
                            attestationPool.removeAll(blockBody.getAttestations());
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
