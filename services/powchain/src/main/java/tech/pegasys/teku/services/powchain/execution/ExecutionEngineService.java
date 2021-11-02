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
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionEngineService implements ChainHeadChannel {

  private static final Logger LOG = LogManager.getLogger();
  private static final ExecutionPayload DEFAULT_EXECUTION_PAYLOAD = new ExecutionPayload();

  private final ExecutionEngineChannel executionEngineChannel;
  private final RecentChainData recentChainData;

  public ExecutionEngineService(
      ExecutionEngineChannel executionEngineChannel, RecentChainData recentChainData) {
    this.executionEngineChannel = executionEngineChannel;
    this.recentChainData = recentChainData;
  }

  public void initialize(EventChannels eventChannels) {
    eventChannels.subscribe(ChainHeadChannel.class, this);
  }

  @Override
  public void chainHeadUpdated(
      UInt64 slot,
      Bytes32 stateRoot,
      Bytes32 headBlockRoot,
      boolean epochTransition,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Optional<ReorgContext> optionalReorgContext) {

    recentChainData
        .retrieveBlockState(headBlockRoot)
        .thenCompose(
            maybeState ->
                maybeState
                    .map(
                        state ->
                            updateForkChoice(
                                headBlockRoot, state.getFinalized_checkpoint().getRoot()))
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .finish(err -> LOG.warn("forkchoiceUpdated failed", err));
  }

  private static Optional<Bytes32> getPayloadBlockHash(Optional<BeaconBlock> block) {
    return block
        .map(BeaconBlock::getBody)
        .flatMap(BeaconBlockBody::toVersionMerge)
        .map(BeaconBlockBodyMerge::getExecutionPayload)
        .filter(executionPayload -> !DEFAULT_EXECUTION_PAYLOAD.equals(executionPayload))
        .map(ExecutionPayload::getBlockHash);
  }

  private SafeFuture<Void> updateForkChoice(Bytes32 headBlockRoot, Bytes32 finalizedBlockRoot) {

    SafeFuture<Optional<Bytes32>> headBlockHashPromise =
        recentChainData
            .retrieveBlockByRoot(headBlockRoot)
            .thenApply(ExecutionEngineService::getPayloadBlockHash);

    SafeFuture<Bytes32> finalizedBlockHashPromise =
        recentChainData
            .retrieveBlockByRoot(finalizedBlockRoot)
            .thenApply(ExecutionEngineService::getPayloadBlockHash)
            .thenApply(maybeHash -> maybeHash.orElse(Bytes32.ZERO));

    return headBlockHashPromise
        .thenCombine(finalizedBlockHashPromise, ForkChoiceUpdate::fromHeadAndFinalized)
        .thenCompose(
            maybeUpd ->
                maybeUpd
                    .map(
                        upd ->
                            executionEngineChannel.forkChoiceUpdated(
                                upd.getHeadBlock(), upd.getFinalizedBlock()))
                    .orElseGet(() -> SafeFuture.completedFuture(null)));
  }

  private static class ForkChoiceUpdate {

    private final Bytes32 headBlock;
    private final Bytes32 finalizedBlock;
    private final Bytes32 confirmedBlock;

    /** @deprecated TODO confirmed block post merge interop */
    @Deprecated
    static Optional<ForkChoiceUpdate> fromHeadAndFinalized(
        Optional<Bytes32> maybeHeadBlock, Bytes32 finalizedBlock) {
      return maybeHeadBlock.map(
          headBlock -> new ForkChoiceUpdate(headBlock, finalizedBlock, Bytes32.ZERO));
    }

    public ForkChoiceUpdate(Bytes32 headBlock, Bytes32 finalizedBlock, Bytes32 confirmedBlock) {
      this.headBlock = headBlock;
      this.finalizedBlock = finalizedBlock;
      this.confirmedBlock = confirmedBlock;
    }

    public Bytes32 getHeadBlock() {
      return headBlock;
    }

    public Bytes32 getFinalizedBlock() {
      return finalizedBlock;
    }

    public Bytes32 getConfirmedBlock() {
      return confirmedBlock;
    }
  }
}
