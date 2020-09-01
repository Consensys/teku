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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

import java.util.NavigableMap;
import java.util.Optional;

public class OperationsReOrgManager implements ReorgEventChannel {
  private static final Logger LOG = LogManager.getLogger();

  final OperationPool<SignedVoluntaryExit> exitPool;
  final OperationPool<ProposerSlashing> proposerSlashingPool;
  final OperationPool<AttesterSlashing> attesterSlashingPool;
  final AttestationManager attestationManager;
  final AggregatingAttestationPool attestationPool;
  final RecentChainData recentChainData;

  public OperationsReOrgManager(
      OperationPool<SignedVoluntaryExit> exitPool,
      OperationPool<ProposerSlashing> proposerSlashingPool,
      OperationPool<AttesterSlashing> attesterSlashingPool,
      AttestationManager attestationManager,
      AggregatingAttestationPool attestationPool,
      RecentChainData recentChainData) {
    this.exitPool = exitPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.attestationManager = attestationManager;
    this.attestationPool = attestationPool;
    this.recentChainData = recentChainData;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void reorgOccurred(
      Bytes32 bestBlockRoot, UInt64 bestSlot, Bytes32 oldBestBlockRoot, UInt64 commonAncestorSlot) {
    NavigableMap<UInt64, Bytes32> notCanonicalBlockRoots =
        recentChainData.getAncestorRootsForRoot(commonAncestorSlot, oldBestBlockRoot);
    notCanonicalBlockRoots.forEach(
        (__, root) -> {
          SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
              recentChainData.retrieveBlockByRoot(root);
          maybeBlockFuture
              .thenAccept(
                  maybeBlock ->
                      maybeBlock.ifPresentOrElse(
                          block -> {
                            BeaconBlockBody blockBody = block.getBody();
                            blockBody.getProposer_slashings().forEach(proposerSlashingPool::add);
                            blockBody.getAttester_slashings().forEach(attesterSlashingPool::add);
                            blockBody.getVoluntary_exits().forEach(exitPool::add);
                            blockBody.getAttestations().forEach(attestationManager::onAttestation);
                          },
                          () ->
                              LOG.warn(
                                  "Failed to re-queue operations for now non-canonical block: {}",
                                  root)))
              .finish(
                  err ->
                      LOG.warn(
                          "Failed to re-queue operations for now non-canonical block: {} due to future error: {}",
                          root,
                          err.getMessage()));
        });

    NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots =
        recentChainData.getAncestorRootsForRoot(commonAncestorSlot, bestBlockRoot);
    nowCanonicalBlockRoots.forEach(
        (__, root) -> {
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
                              LOG.warn(
                                  "Failed to remove operations from pools for now canonical block: {}",
                                  root)))
              .finish(
                  err ->
                      LOG.warn(
                          "Failed to remove operations from pools for now canonical block: {} due to future error: {}",
                          root,
                          err.getMessage()));
        });
  }
}
