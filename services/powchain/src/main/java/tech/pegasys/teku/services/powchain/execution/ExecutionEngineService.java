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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
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
  private static final ExecutionPayload DEFAULT_EXECUTION_PAYLOAD = new ExecutionPayload();

  private final ExecutionEngineChannel executionEngineChannel;
  private final RecentChainData recentChainData;

  private Optional<Bytes32> lastBestBlock;
  private Optional<Bytes32> lastFinalizedBlock;

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

    final Optional<Bytes32> curFinalizedBlock;

    synchronized (this) {
      lastBestBlock = Optional.of(bestBlockRoot);
      curFinalizedBlock = lastFinalizedBlock;
    }

    updateForkChoice(bestBlockRoot, curFinalizedBlock);
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint) {
    final Bytes32 curBestBlock;
    final Optional<Bytes32> curFinalizedBlock;

    synchronized (this) {
      lastFinalizedBlock = Optional.of(checkpoint.getRoot());
      if (lastBestBlock.isEmpty()) {
        return;
      }
      curBestBlock = lastBestBlock.get();
      curFinalizedBlock = lastFinalizedBlock;
    }

    updateForkChoice(curBestBlock, curFinalizedBlock);
  }

  private static Optional<Bytes32> getPowBlockHash(Optional<BeaconBlock> block) {
    return block
        .map(BeaconBlock::getBody)
        .flatMap(BeaconBlockBody::toVersionMerge)
        .map(BeaconBlockBodyMerge::getExecution_payload)
        .filter(executionPayload -> !DEFAULT_EXECUTION_PAYLOAD.equals(executionPayload))
        .map(ExecutionPayload::getBlock_hash);
  }

  private void updateForkChoice(Bytes32 bestBlockRoot, Optional<Bytes32> maybeFinalizedBlockRoot) {

    SafeFuture<Optional<Bytes32>> bestBlockHashPromise =
        recentChainData
            .retrieveBlockByRoot(bestBlockRoot)
            .thenApply(ExecutionEngineService::getPowBlockHash);

    SafeFuture<Bytes32> finalizedBlockHashPromise =
        maybeFinalizedBlockRoot
            .map(recentChainData::retrieveBlockByRoot)
            .orElseGet(() -> SafeFuture.completedFuture(Optional.empty()))
            .thenApply(ExecutionEngineService::getPowBlockHash)
            .thenApply(maybeHash -> maybeHash.orElse(Bytes32.ZERO));

    bestBlockHashPromise
        .thenCombine(finalizedBlockHashPromise, ForkChoiceUpdate::fromBestAndFinalized)
        .thenCompose(
            maybeUpd ->
                maybeUpd
                    .map(
                        upd ->
                            executionEngineChannel.forkChoiceUpdated(
                                upd.getBestBlock(), upd.getFinalizedBlock()))
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .finish(err -> LOG.warn("forkChoiceUpdated failed", err));
  }

  private static class ForkChoiceUpdate {

    private final Bytes32 bestBlock;
    private final Bytes32 finalizedBlock;
    private final Bytes32 confirmedBlock;

    /** @deprecated TODO confirmed block post merge interop */
    @Deprecated
    static Optional<ForkChoiceUpdate> fromBestAndFinalized(
        Optional<Bytes32> maybeBestBlock, Bytes32 finalizedBlock) {
      return maybeBestBlock.map(
          bestBlock -> new ForkChoiceUpdate(bestBlock, finalizedBlock, Bytes32.ZERO));
    }

    public ForkChoiceUpdate(Bytes32 bestBlock, Bytes32 finalizedBlock, Bytes32 confirmedBlock) {
      this.bestBlock = bestBlock;
      this.finalizedBlock = finalizedBlock;
      this.confirmedBlock = confirmedBlock;
    }

    public Bytes32 getBestBlock() {
      return bestBlock;
    }

    public Bytes32 getFinalizedBlock() {
      return finalizedBlock;
    }

    public Bytes32 getConfirmedBlock() {
      return confirmedBlock;
    }
  }
}
