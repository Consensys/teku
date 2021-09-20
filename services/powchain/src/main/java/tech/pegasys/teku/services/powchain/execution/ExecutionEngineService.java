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

package tech.pegasys.teku.services.powchain.execution;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionEngineService implements ChainHeadChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineChannel executionEngineChannel;
  private final RecentChainData recentChainData;

  public ExecutionEngineService(
      ExecutionEngineChannel executionEngineChannel, RecentChainData recentChainData) {
    this.executionEngineChannel = executionEngineChannel;
    this.recentChainData = recentChainData;
  }

  public void initialize(EventChannels eventChannels) {
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
  }

  @Override
  public void chainHeadUpdated(
      UInt64 slot,
      Bytes32 stateRoot,
      Bytes32 bestBlockRoot,
      boolean epochTransition,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Optional<ReorgContext> optionalReorgContext) {

    recentChainData
        .retrieveBlockByRoot(bestBlockRoot)
        .thenCompose(
            maybeABlock ->
                maybeABlock
                    .flatMap(block -> block.getBody().toVersionMerge())
                    .map(this::onHeadBlockMerge)
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .finish(err -> LOG.warn("setHead failed", err));
  }

  private SafeFuture<Void> onHeadBlockMerge(BeaconBlockBodyMerge blockBody) {
    // Check if there is a payload
    if (!blockBody.getExecution_payload().equals(new ExecutionPayload())) {
      return executionEngineChannel.setHead(blockBody.getExecution_payload().getBlock_hash());
    } else {
      return SafeFuture.completedFuture(null);
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint) {
    recentChainData
        .retrieveBlockByRoot(checkpoint.getRoot())
        .thenCompose(
            maybeABlock ->
                maybeABlock
                    .flatMap(block -> block.getBody().toVersionMerge())
                    .map(this::onFinalizedBlockMerge)
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .finish(err -> LOG.warn("finalizeBlock failed", err));
  }

  private SafeFuture<Void> onFinalizedBlockMerge(BeaconBlockBodyMerge blockBody) {
    // Check if there is a payload
    if (!blockBody.getExecution_payload().equals(new ExecutionPayload())) {
      return executionEngineChannel.finalizeBlock(blockBody.getExecution_payload().getBlock_hash());
    } else {
      return SafeFuture.completedFuture(null);
    }
  }
}
